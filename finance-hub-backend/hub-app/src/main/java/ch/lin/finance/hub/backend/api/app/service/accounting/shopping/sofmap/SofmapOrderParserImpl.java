/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.common.exception.SofmapOrderParseException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapShipment;

/**
 * Implementation of {@link SofmapOrderParser}.
 * <p>
 * Uses Jsoup to extract order ID, dates, financial summaries, shipments, and
 * items
 * from a Sofmap Order Detail HTML page.
 */
@Component
public class SofmapOrderParserImpl implements SofmapOrderParser {

    /** Logger for this parser. */
    private static final Logger logger = LoggerFactory.getLogger(SofmapOrderParserImpl.class);

    /** Formatter for Japanese date format (yyyy年M月d日). */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN);

    /** Regex pattern to extract the order date. */
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}年\\d{1,2}月\\d{1,2}日)");

    @Override
    public SofmapOrder parse(File htmlFile) {
        logger.info("Parsing Sofmap order file: {}", htmlFile.getName());
        Document doc;
        try {
            // Sofmap HTML is often Shift_JIS, but Jsoup can autodetect from meta charset
            // if we pass null. However, to be safe since we observed shift_jis in python,
            // we specify it.
            doc = Jsoup.parse(htmlFile, "Shift_JIS");
        } catch (IOException e) {
            logger.error("Failed to read file: {}", htmlFile.getAbsolutePath(), e);
            throw new SofmapOrderParseException("File read error: " + e.getMessage(), e);
        }

        // 1. Parse Order ID
        String orderId = parseOrderId(doc);
        if (orderId == null || orderId.isBlank()) {
            String errMsg = "Missing mandatory field: Order ID";
            logger.error("Parse failed for {}: {}", htmlFile.getName(), errMsg);
            throw new SofmapOrderParseException(errMsg);
        }

        // 2. Parse Order Date
        LocalDate orderDate = parseOrderDate(doc);
        if (orderDate == null) {
            String errMsg = "Missing or unparseable mandatory field: Order Date";
            logger.error("Parse failed for {}: {}", htmlFile.getName(), errMsg);
            throw new SofmapOrderParseException(errMsg);
        }

        SofmapOrder order = new SofmapOrder(orderId, orderDate);

        // 3. Parse Financial Summary
        boolean hasTotal = parseOrderSummary(doc, order);
        if (!hasTotal) {
            String errMsg = "Missing mandatory field: Total Amount";
            logger.error("Parse failed for {}: {}", htmlFile.getName(), errMsg);
            throw new SofmapOrderParseException(errMsg);
        }

        // 4. Parse Payment Method & Shipment (Often located in the same dl grid as
        // order date/id)
        parsePaymentAndShipment(doc, order);

        // 5. Parse Items
        parseItems(doc, order);

        // 6. Verify parsed data math
        verifyOrderMath(order, htmlFile.getName());

        return order;
    }

    private String parseOrderId(Document doc) {
        Element dt = doc.selectFirst("dt:containsOwn(ご注文番号)");
        if (dt != null && dt.nextElementSibling() != null) {
            return dt.nextElementSibling().text().trim();
        }
        return null;
    }

    private LocalDate parseOrderDate(Document doc) {
        Element dt = doc.selectFirst("dt:containsOwn(ご注文日)");
        if (dt != null && dt.nextElementSibling() != null) {
            String text = dt.nextElementSibling().text().trim();
            Matcher matcher = DATE_PATTERN.matcher(text);
            if (matcher.find()) {
                try {
                    return LocalDate.parse(matcher.group(1), DATE_FORMATTER);
                } catch (Exception e) {
                    logger.warn("Failed to parse date string: {}", text);
                }
            }
        }
        return null;
    }

    private boolean parseOrderSummary(Document doc, SofmapOrder order) {
        boolean hasTotal = false;
        boolean hasBilled = false;
        // Table body containing the summary
        Elements headers = doc.select("th");
        for (Element th : headers) {
            String text = th.text().trim();
            Element td = th.nextElementSibling();
            if (td == null || !td.tagName().equalsIgnoreCase("td")) {
                continue;
            }

            BigDecimal amount = parseMoney(td.text());

            if (text.contains("ご注文合計") || text.contains("ご注文金額")) {
                // If there are both, prefer ご注文合計
                if (!hasTotal || text.contains("ご注文合計")) {
                    order.setTotalAmount(amount);
                    hasTotal = true;
                }
            } else if (text.contains("お支払い総額") || text.contains("お支払い金額")) {
                order.setBilledAmount(amount);
                hasBilled = true;
            } else if (text.contains("送料合計")) {
                order.setShippingFee(amount);
            } else if (text.contains("ソフマップポイント値引") || text.contains("利用ポイント")) {
                order.setPointsUsed(amount.abs());
            } else if (text.contains("加算ポイント")) {
                order.setEarnedPoints(amount.abs());
            }
        }

        // Check billed amount fallback if not explicitly set
        if (hasTotal && !hasBilled) {
            BigDecimal points = order.getPointsUsed();
            order.setBilledAmount(order.getTotalAmount().subtract(points));
        }
        return hasTotal;
    }

    private void parsePaymentAndShipment(Document doc, SofmapOrder order) {
        // Payment
        Element paymentDt = doc.selectFirst("dt:containsOwn(お支払い方法)");
        if (paymentDt != null && paymentDt.nextElementSibling() != null) {
            String method = paymentDt.nextElementSibling().text().trim();
            BigDecimal billedAmount = order.getBilledAmount();
            if (billedAmount.compareTo(BigDecimal.ZERO) > 0) {
                SofmapPayment payment = new SofmapPayment(method);
                payment.setAmount(billedAmount);
                order.addPayment(payment);
            }
        }

        // Shipment
        Element shipmentDt = doc.selectFirst("dt:containsOwn(出荷状況)");
        if (shipmentDt != null && shipmentDt.nextElementSibling() != null) {
            String status = shipmentDt.nextElementSibling().text().trim();

            // Sofmap usually doesn't show tracking number/carrier in the main page easily
            // We default shipping date to order date if not specified.
            LocalDate shippingDate = order.getOrderDate();
            SofmapShipment shipment = new SofmapShipment(shippingDate, "Sofmap Default", null, status);
            order.addShipment(shipment);
            order.setShippingDate(shippingDate);
        }
    }

    private void parseItems(Document doc, SofmapOrder order) {
        Elements productBoxes = doc.select(".product_box");
        for (Element box : productBoxes) {
            Element nameEl = box.selectFirst(".product_name");
            if (nameEl == null || nameEl.text().trim().isBlank()) {
                continue;
            }

            SofmapItem item = new SofmapItem(nameEl.text().trim());

            Element priceDt = box.selectFirst("dt:containsOwn(販売価格)");
            if (priceDt != null && priceDt.nextElementSibling() != null) {
                item.setUnitPrice(parseMoney(priceDt.nextElementSibling().text()));
            }

            Element qtyDt = box.selectFirst("dt:containsOwn(数量)");
            if (qtyDt != null && qtyDt.nextElementSibling() != null) {
                String qtyText = qtyDt.nextElementSibling().text().replaceAll("[^0-9]", "");
                if (!qtyText.isEmpty()) {
                    try {
                        item.setQuantity(Integer.valueOf(qtyText));
                    } catch (NumberFormatException ignored) {
                        item.setQuantity(1);
                    }
                } else {
                    item.setQuantity(1);
                }
            } else {
                item.setQuantity(1);
            }

            order.addItem(item);
        }
    }

    private BigDecimal parseMoney(String text) {
        String clean = text.replaceAll("[^0-9.-]", "");
        if (clean.isBlank())
            return BigDecimal.ZERO;
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse money value: {}", text);
            return BigDecimal.ZERO;
        }
    }

    private void verifyOrderMath(SofmapOrder order, String fileName) {
        BigDecimal total = order.getTotalAmount();
        BigDecimal billed = order.getBilledAmount();
        BigDecimal points = order.getPointsUsed();
        BigDecimal discounts = order.getDiscounts();

        BigDecimal calculatedBilled = total.subtract(points).subtract(discounts);

        if (calculatedBilled.compareTo(billed) != 0) {
            String errMsg = String.format("Math validation failed! Expected Billed: %s, Parsed Billed: %s. "
                    + "(Total: %s, Points: %s, Discounts: %s)",
                    calculatedBilled, billed, total, points, discounts);
            logger.warn("Validation warning for order {} in {}: {}", order.getOrderId(), fileName, errMsg);
            order.setNeedsReview(true);
            order.setReviewReason(errMsg);
        }
    }
}
