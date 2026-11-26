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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.common.exception.RakutenOrderParseException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenPayment;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for extracting Rakuten order details from HTML receipts.
 */
@Service
@Slf4j
public class RakutenOrderParserImpl implements RakutenOrderParser {

    /** Formatter for Japanese date format (yyyy/MM/dd). */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.JAPAN);

    /** Regex pattern to extract the order ID. */
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("注文番号[^\\d]*([\\d-]+)");

    /** Regex pattern to extract the order date (format 1). */
    private static final Pattern ORDER_DATE_PATTERN = Pattern.compile("注文日時[^\\d]*(\\d{4}/\\d{2}/\\d{2})");
    /** Regex pattern to extract the order date (format 2). */
    private static final Pattern ORDER_DATE_PATTERN_2 = Pattern
            .compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日[^\\d]*(?:ご注文|更新)");

    /** Regex pattern to extract the total amount before discounts. */
    private static final Pattern TOTAL_AMOUNT_PATTERN = Pattern.compile("(?:小計|商品合計金額)[^\\d]*([\\d,]+)[^\\d]*円");

    /** Regex pattern to extract consumption tax. */
    private static final Pattern TAX_PATTERN = Pattern.compile("消費税[^円]*?([\\d,]+)\\s*円");

    /** Regex pattern to extract shipping fee. */
    private static final Pattern SHIPPING_PATTERN = Pattern.compile("送料[^円\\d]*?(無料|送料無料|[\\d,]+)(?:\\s*円)?");

    /** Regex pattern to extract discounts (coupons). */
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile("クーポン利用(?!額)[^円]*?[-]?\\s*([\\d,]+)\\s*円");

    /** Regex pattern to extract used Rakuten points. */
    private static final Pattern POINTS_USED_PATTERN = Pattern.compile("ポイント利用(?!額)[^\\d]*([\\d,]+)[^\\d]*(?:円|ポイント)");
    /** Regex pattern to extract the final billed amount. */
    private static final Pattern BILLED_AMOUNT_PATTERN = Pattern.compile("(?:支払い金額|総合計)[^\\d]*([\\d,]+)[^\\d]*円");

    /** Regex pattern to extract item quantity. */
    private static final Pattern QTY_PATTERN = Pattern.compile("(?:数量|個数)[^\\d]*(\\d+)");

    /** Regex pattern to extract unit price. */
    private static final Pattern PRICE_PATTERN = Pattern.compile("([\\d,]+)\\s*円");

    /** Regex pattern to extract delivery date. */
    private static final Pattern DELIVERY_DATE_PATTERN = Pattern.compile("(\\d{2})/(\\d{2})");

    @Override
    public RakutenOrder parse(File htmlFile) throws Exception {
        if (htmlFile == null || !htmlFile.exists()) {
            throw new IllegalArgumentException("HTML file cannot be null and must exist.");
        }

        Document doc = Jsoup.parse(htmlFile, StandardCharsets.UTF_8.name());

        // Attempt to extract window.__INITIAL_STATE__
        Elements scripts = doc.select("script");
        JsonNode initialState = null;
        ObjectMapper objectMapper = new ObjectMapper();
        for (Element script : scripts) {
            String scriptHtml = script.html();
            if (scriptHtml.contains("window.__INITIAL_STATE__")) {
                Matcher m = Pattern.compile("window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\});?\\s*$", Pattern.DOTALL)
                        .matcher(scriptHtml);
                if (m.find()) {
                    try {
                        initialState = objectMapper.readTree(m.group(1));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.warn("Failed to parse __INITIAL_STATE__ json", e);
                    }
                }
                break;
            }
        }

        // We can often just use text content for finding these values, similar to the
        // JS parser.
        // It's robust against DOM changes inside the specific containers.
        String allText = doc.body().text();

        String orderId = extractUsingPattern(allText, ORDER_ID_PATTERN);
        if (orderId == null || orderId.isBlank()) {
            String errMsg = "Missing mandatory field: Order ID";
            log.error("Parse failed for {}: {}", htmlFile.getName(), errMsg);
            throw new RakutenOrderParseException(errMsg);
        }

        String orderDateStr = extractUsingPattern(allText, ORDER_DATE_PATTERN);
        LocalDate orderDate = LocalDate.now();
        if (orderDateStr != null) {
            orderDate = LocalDate.parse(orderDateStr, DATE_FORMATTER);
        } else {
            Matcher m2 = ORDER_DATE_PATTERN_2.matcher(allText);
            if (m2.find()) {
                String y = m2.group(1);
                String m = m2.group(2).length() == 1 ? "0" + m2.group(2) : m2.group(2);
                String d = m2.group(3).length() == 1 ? "0" + m2.group(3) : m2.group(3);
                orderDate = LocalDate.parse(y + "/" + m + "/" + d, DATE_FORMATTER);
            }
        }

        RakutenOrder order = new RakutenOrder(orderId, orderDate);

        // Restrict amount parsing to the summary section if possible
        String summaryText = allText;
        int summaryStart = -1;

        int[] summaryKeywords = {
                allText.indexOf("商品合計金額"),
                allText.indexOf("支払い金額"),
                allText.indexOf("総合計"),
                allText.indexOf("請求金額")
        };

        for (int idx : summaryKeywords) {
            if (idx != -1) {
                if (summaryStart == -1 || idx < summaryStart) {
                    summaryStart = idx;
                }
            }
        }

        if (summaryStart != -1) {
            int lastShoukei = allText.lastIndexOf("小計", summaryStart);
            if (lastShoukei != -1) {
                summaryStart = lastShoukei;
            }
        }

        if (summaryStart != -1) {
            summaryText = allText.substring(summaryStart);
        }

        // Amounts
        String billedStr = extractUsingPattern(summaryText, BILLED_AMOUNT_PATTERN);
        if (billedStr != null) {
            order.setBilledAmount(new BigDecimal(billedStr.replace(",", "")));
        } else {
            order.setBilledAmount(BigDecimal.ZERO);
        }

        String totalStr = extractLastUsingPattern(summaryText, TOTAL_AMOUNT_PATTERN);
        if (totalStr != null) {
            order.setTotalAmount(new BigDecimal(totalStr.replace(",", "")));
        } else {
            order.setTotalAmount(order.getBilledAmount());
        }

        // TAX
        String taxStr = extractUsingPattern(summaryText, TAX_PATTERN);
        BigDecimal tax = BigDecimal.ZERO;
        if (taxStr != null) {
            tax = new BigDecimal(taxStr.replace(",", ""));
        }
        order.setTaxAmount(tax);

        // SHIPPING
        String shippingStr = extractUsingPattern(summaryText, SHIPPING_PATTERN);
        if (shippingStr != null) {
            if (shippingStr.contains("無料")) {
                order.setShippingFee(BigDecimal.ZERO);
            } else {
                order.setShippingFee(new BigDecimal(shippingStr.replace(",", "")));
            }
        } else {
            order.setShippingFee(BigDecimal.ZERO);
        }

        // DISCOUNT
        String discountStr = extractUsingPattern(summaryText, DISCOUNT_PATTERN);
        if (discountStr != null) {
            order.setDiscounts(new BigDecimal(discountStr.replace(",", "")));
        } else {
            order.setDiscounts(BigDecimal.ZERO);
        }

        String pointsStr = extractUsingPattern(summaryText, POINTS_USED_PATTERN);
        if (pointsStr != null) {
            order.setPointsUsed(new BigDecimal(pointsStr.replace(",", "")));
        } else {
            order.setPointsUsed(BigDecimal.ZERO);
        }

        // Add payment method. If billed > 0, we must have a payment method
        if (order.getBilledAmount().compareTo(BigDecimal.ZERO) > 0) {
            String paymentMethod = "Credit Card";
            if (allText.contains("代金引換")) {
                paymentMethod = "代金引換";
            } else if (allText.contains("コンビニ")) {
                paymentMethod = "コンビニ・ATM・ネットバンキング・電子マネー払い";
            } else if (allText.contains("銀行振込")) {
                paymentMethod = "銀行振込";
            } else if (allText.contains("後払い")) {
                paymentMethod = "後払い決済";
            }

            RakutenPayment payment = new RakutenPayment(paymentMethod);
            payment.setAmount(order.getBilledAmount());
            order.addPayment(payment);
        }

        // Parse Items
        Elements links = doc.select("a[href*='item.rakuten.co.jp'], a[href*='books.rakuten.co.jp/rb/']");
        List<String> seenHrefs = new ArrayList<>();

        for (Element link : links) {
            String href = link.attr("href");
            if (seenHrefs.contains(href)) {
                continue;
            }

            String itemName = link.text().trim();
            if (itemName.length() > 5 && !itemName.equalsIgnoreCase("No Image")) { // Ensure it's a real item link and
                                                                                   // not just an icon or small text
                seenHrefs.add(href);

                RakutenItem item = new RakutenItem(itemName);

                // Extract seller and item ID from URL
                try {
                    String path = new java.net.URI(href).getPath();
                    if (path != null) {
                        String[] parts = path.split("/");
                        if (parts.length >= 3) {
                            item.setSeller(parts[1]);
                            item.setItemId(parts[2]);
                        }
                    }
                } catch (java.net.URISyntaxException | NullPointerException e) {
                    log.warn("Failed to parse href for item details: {}", href);
                }

                // Look for quantity and price by walking up the tree
                Element container = link.parent();
                int depth = 0;
                boolean foundDetails = false;
                while (container != null && depth < 10) {
                    String containerText = container.text();
                    if ((containerText.contains("数量") || containerText.contains("個数")) && containerText.contains("円")) {
                        String priceStr = extractUsingPattern(containerText, PRICE_PATTERN);
                        if (priceStr != null) {
                            item.setUnitPrice(new BigDecimal(priceStr.replace(",", "")));
                        } else {
                            item.setUnitPrice(BigDecimal.ZERO);
                        }

                        String qtyStr = extractUsingPattern(containerText, QTY_PATTERN);
                        if (qtyStr != null) {
                            int quantity = Integer.parseInt(qtyStr);
                            item.setQuantity(quantity);
                        } else {
                            item.setQuantity(1);
                        }

                        foundDetails = true;
                        break;
                    }
                    container = container.parent();
                    depth++;
                }

                if (!foundDetails) {
                    // Fallback
                    item.setUnitPrice(BigDecimal.ZERO);
                    item.setQuantity(1);
                }

                order.addItem(item);
            }
        }

        if (initialState != null) {
            JsonNode orderData = initialState.path("orderData");
            if (!orderData.isMissingNode()) {

                LocalDate shippedDate = order.getOrderDate();
                JsonNode orderStatusObject = orderData.path("orderStatusObject");
                if (!orderStatusObject.isMissingNode()) {
                    JsonNode statusHistory = orderStatusObject.path("statusHistory");
                    if (statusHistory.isArray()) {
                        for (JsonNode status : statusHistory) {
                            String name = status.path("name").asText("");
                            if ("OPP_ORDER_STATUS_DELIVERED".equals(name) || "OPP_ORDER_STATUS_SHIPPED".equals(name)) {
                                String dateStr = status.path("date").asText("");
                                if (dateStr.length() >= 10) {
                                    try {
                                        shippedDate = LocalDate.parse(dateStr.substring(0, 10).replace("/", "-"));
                                    } catch (Exception e) {
                                        log.warn("Failed to parse statusHistory date: {}", dateStr);
                                    }
                                }
                            }
                        }
                    }
                }

                JsonNode shippingList = orderData.path("shippingList");
                if (shippingList.isArray()) {
                    for (JsonNode shippingNode : shippingList) {
                        String trackingNumber = shippingNode.path("trackingNumber").asText("");
                        JsonNode deliveryInfo = shippingNode.path("deliveryInfo");
                        String carrier = deliveryInfo.path("deliveryCarrierName").asText("");
                        String deliveryDateTime = deliveryInfo.path("deliveryDateTime").asText("");

                        LocalDate finalShippingDate = shippedDate;
                        if (!deliveryDateTime.isEmpty()) {
                            Matcher dtMatch = DELIVERY_DATE_PATTERN.matcher(deliveryDateTime);
                            if (dtMatch.find()) {
                                int month = Integer.parseInt(dtMatch.group(1));
                                int day = Integer.parseInt(dtMatch.group(2));
                                int orderYear = order.getOrderDate().getYear();
                                int orderMonth = order.getOrderDate().getMonthValue();

                                if (month < orderMonth - 2) {
                                    orderYear++;
                                }
                                try {
                                    finalShippingDate = LocalDate.of(orderYear, month, day);
                                } catch (Exception e) {
                                    log.warn("Failed to create date from deliveryDateTime: {}", deliveryDateTime);
                                }
                            }
                        }

                        if (!trackingNumber.isEmpty()) {
                            ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenShipment shipment = new ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenShipment(
                                    finalShippingDate, carrier, trackingNumber);
                            order.addShipment(shipment);
                        }
                    }
                }
            }
        }

        verifyOrderMath(order, htmlFile.getName());

        return order;
    }

    /**
     * Verifies if the parsed financial numbers add up correctly. Logs a warning
     * if there is a mismatch, indicating potential parsing issues or new page
     * formats.
     *
     * @param order    The parsed {@link RakutenOrder}.
     * @param fileName The name of the source file (for logging).
     */
    private void verifyOrderMath(RakutenOrder order, String fileName) {
        if (order.getTotalAmount() == null) {
            order.setNeedsReview(true);
            order.setReviewReason("Cannot verify math: Total amount is missing");
            log.warn("Cannot verify math for order {} in {}: Total amount is missing", order.getOrderId(), fileName);
            return;
        }

        BigDecimal total = order.getTotalAmount();
        BigDecimal billed = order.getBilledAmount() != null ? order.getBilledAmount() : BigDecimal.ZERO;
        BigDecimal points = order.getPointsUsed() != null ? order.getPointsUsed() : BigDecimal.ZERO;
        BigDecimal discounts = order.getDiscounts() != null ? order.getDiscounts() : BigDecimal.ZERO;

        BigDecimal tax = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal shipping = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;

        // Formula: Subtotal(Total) + Tax + Shipping - Discounts - Points = BilledAmount
        BigDecimal calculatedBilled = total.add(tax).add(shipping).subtract(discounts).subtract(points);

        if (calculatedBilled.compareTo(billed) != 0) {
            String errMsg = String.format("Math validation failed! Expected Billed: %s, Parsed Billed: %s. "
                    + "(Total: %s, Tax: %s, Shipping: %s, Discounts: %s, Points: %s)",
                    calculatedBilled, billed, total, tax, shipping, discounts, points);
            log.warn("Validation warning for order {} in {}: {}", order.getOrderId(), fileName, errMsg);
            order.setNeedsReview(true);
            order.setReviewReason(errMsg);
        } else {
            order.setNeedsReview(false);
            order.setReviewReason(null);
            log.info("Math validation passed for order {} in {}", order.getOrderId(), fileName);
        }
    }

    private String extractUsingPattern(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractLastUsingPattern(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        String lastMatch = null;
        while (m.find()) {
            lastMatch = m.group(1);
        }
        return lastMatch;
    }
}
