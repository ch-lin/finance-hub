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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.common.exception.AmazonOrderParseException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonShipment;

/**
 * Implementation of {@link AmazonOrderParser}.
 * <p>
 * Uses Jsoup to extract order ID, dates, financial summaries, shipments, and
 * items
 * from an Amazon Order Detail HTML page. Handles various page formats including
 * modern Next.js/React structures and legacy formats.
 */
@Component
public class AmazonOrderParserImpl implements AmazonOrderParser {

    /** Logger for this parser. */
    private static final Logger logger = LoggerFactory.getLogger(AmazonOrderParserImpl.class);

    /** Formatter for Japanese date format (yyyy年M月d日). */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN);
    /** Regex pattern to extract the ASIN or item ID from URLs. */
    private static final Pattern ASIN_PATTERN = Pattern.compile("/(?:dp|gp/product|product)/([A-Z0-9]{10})");
    /** Regex pattern to extract credit card brand and last 4 digits. */
    private static final Pattern CREDIT_CARD_PATTERN = Pattern
            .compile("(?i)(Visa|MasterCard|JCB|Amex|American Express|Diners)[\\s\\S]*?(\\d{4})");

    /**
     * {@inheritDoc}
     * <p>
     * Throws {@link AmazonOrderParseException} if mandatory fields (Order ID, Date,
     * Total Amount)
     * cannot be resolved. It also performs a mathematical verification of the
     * amounts at the end.
     */
    @Override
    public AmazonOrder parse(File htmlFile) {
        logger.info("Parsing Amazon order file: {}", htmlFile.getName());
        Document doc;
        try {
            doc = Jsoup.parse(htmlFile, "UTF-8");
        } catch (IOException e) {
            logger.error("Failed to read file: {}", htmlFile.getAbsolutePath(), e);
            throw new AmazonOrderParseException("File read error: " + e.getMessage(), e);
        }

        // 1. Parse Order ID
        String orderId = parseOrderId(doc);
        if (orderId == null || orderId.isBlank()) {
            String errMsg = "Missing mandatory field: Order ID";
            logger.error("Parse failed for {}: {}", htmlFile.getName(), errMsg);
            throw new AmazonOrderParseException(errMsg);
        }

        // 2. Parse Order Date
        LocalDate orderDate = parseOrderDate(doc);
        if (orderDate == null) {
            String errMsg = "Missing or unparseable mandatory field: Order Date";
            logger.error("Parse failed for {}: {}", htmlFile.getName(), errMsg);
            throw new AmazonOrderParseException(errMsg);
        }

        // 3. Parse Order Summary (including total, discounts, etc.)
        AmazonOrder order = new AmazonOrder(orderId, orderDate);

        // 3. Parse Shipments (Optional, might be empty on modern Next.js pages)
        List<AmazonShipment> shipments = parseShipments(doc, orderDate);
        LocalDate latestShippingDate = null;
        for (AmazonShipment shipment : shipments) {
            order.addShipment(shipment);
            if (latestShippingDate == null || shipment.getShippingDate().isAfter(latestShippingDate)) {
                latestShippingDate = shipment.getShippingDate();
            }
        }
        if (latestShippingDate != null) {
            order.setShippingDate(latestShippingDate);
        }

        // 4. Parse Items
        List<AmazonItem> items = parseItems(doc);
        items.forEach(order::addItem);

        // 5. Parse Order Summary (including total, discounts, etc.)
        parseOrderSummary(doc, order);

        // 6. Verify parsed data math
        verifyOrderMath(order, htmlFile.getName());

        return order;
    }

    /**
     * Extracts the Amazon Order ID from the document.
     *
     * @param doc The parsed HTML document.
     * @return The order ID, or null if not found.
     */
    private String parseOrderId(Document doc) {
        Element element = doc.selectFirst("div[data-component='orderId'] span");
        return element != null ? element.text().trim() : null;
    }

    /**
     * Extracts the Order Date from the document.
     *
     * @param doc The parsed HTML document.
     * @return The parsed {@link LocalDate}, or null if not found.
     */
    private LocalDate parseOrderDate(Document doc) {
        Element element = doc.selectFirst("div[data-component='orderDate'] span");
        if (element != null) {
            String text = element.text().trim();
            Matcher matcher = Pattern.compile("(\\d{4}年\\d{1,2}月\\d{1,2}日)").matcher(text);
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

    /**
     * Extracts shipment information including dates, carriers, and tracking
     * numbers.
     *
     * @param doc       The parsed HTML document.
     * @param orderDate The date the order was placed (used as a fallback for year
     *                  resolution).
     * @return A list of parsed {@link AmazonShipment} objects.
     */
    private List<AmazonShipment> parseShipments(Document doc, LocalDate orderDate) {
        List<AmazonShipment> shipments = new ArrayList<>();
        Elements statusElements = doc.select("div[data-component='shipmentStatus']");

        for (Element element : statusElements) {
            String text = element.text().trim();
            LocalDate parsedDate = null;

            // Try to match the full date format (e.g. 2024年12月21日)
            Matcher fullDateMatcher = Pattern.compile("(\\d{4}年\\d{1,2}月\\d{1,2}日)").matcher(text);
            if (fullDateMatcher.find()) {
                try {
                    parsedDate = LocalDate.parse(fullDateMatcher.group(1), DATE_FORMATTER);
                } catch (Exception e) {
                    logger.warn("Failed to parse full shipping date string: {}", text);
                }
            } else {
                // Fallback for short date format missing the year (e.g. 12月21日に発送)
                Matcher shortDateMatcher = Pattern.compile("(\\d{1,2}月\\d{1,2}日)").matcher(text);
                if (shortDateMatcher.find()) {
                    try {
                        int year = orderDate != null ? orderDate.getYear() : LocalDate.now().getYear();
                        String fullDateStr = year + "年" + shortDateMatcher.group(1);
                        parsedDate = LocalDate.parse(fullDateStr, DATE_FORMATTER);

                        // Handle year crossing (e.g., Ordered in late Dec, shipped in early Jan)
                        if (orderDate != null && parsedDate.isBefore(orderDate.minusMonths(6))) {
                            parsedDate = parsedDate.plusYears(1);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse short shipping date string: {}", text);
                    }
                }
            }

            if (parsedDate != null) {
                String carrier = null;
                String trackingNumber = null;

                // Best effort to extract carrier and tracking number from the surrounding
                // shipment box
                Element container = element.closest(".a-box");
                if (container != null) {
                    String containerText = container.text();

                    Matcher carrierMatcher = Pattern.compile("配送業者[\\s:：]*([^\\s]+)").matcher(containerText);
                    if (carrierMatcher.find()) {
                        carrier = carrierMatcher.group(1);
                    }

                    Matcher trackingMatcher = Pattern.compile("トラッキングID[\\s:：]*([A-Za-z0-9-]+)").matcher(containerText);
                    if (trackingMatcher.find()) {
                        trackingNumber = trackingMatcher.group(1);
                    }
                }

                shipments.add(new AmazonShipment(parsedDate, carrier, trackingNumber));
            }
        }
        return shipments;
    }

    /**
     * Extracts the financial summary (total, subtotal, discounts, points, gift
     * cards, billed amount)
     * and sets them on the order object.
     *
     * @param doc   The parsed HTML document.
     * @param order The {@link AmazonOrder} to populate.
     */
    private void parseOrderSummary(Document doc, AmazonOrder order) {
        Elements rows = doc.select("div[data-component='orderSummary'] .od-line-item-row");
        boolean hasBilledAmount = false;
        for (Element row : rows) {
            Element label = row.selectFirst(".od-line-item-row-label");
            Element content = row.selectFirst(".od-line-item-row-content span");

            if (label == null || content == null) {
                continue;
            }

            String labelText = label.text();
            BigDecimal amount = parseMoney(content.text());

            if (labelText.contains("ご請求額") || labelText.contains("この注文の合計")) {
                order.setBilledAmount(amount);
                hasBilledAmount = true;

                // For digital orders, "この注文の合計" often acts as the total amount as well.
                if (labelText.contains("この注文の合計")
                        && (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) == 0)) {
                    order.setTotalAmount(amount);
                }
                // Create a Payment object for the main payment method.
                // Reason: The "Payment Method" section on Amazon pages usually only shows the
                // instrument name (e.g., Visa), without specific amounts.
                // The actual amount charged to this instrument equals "Billed Amount" after
                // deducting points/gift cards in the summary.
                // Only add the main payment method to the list if the billed amount is greater
                // than 0.
                // This prevents adding a 0-amount credit card payment when fully paid by
                // points/gift cards.
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    AmazonPayment payment = new AmazonPayment(parseMainPaymentMethod(doc));
                    payment.setAmount(amount);
                    order.addPayment(payment);
                }
                // Handle both physical ("注文合計") and digital/free orders ("この注文の合計")
            } else if (labelText.contains("注文合計") || labelText.contains("注文の合計")) {
                order.setTotalAmount(amount);
            } else if (labelText.contains("商品の小計")) {
                // Fallback for Digital orders which lack "注文合計". Set total to subtotal.
                // Will be overwritten safely by "注文合計" later if it's a physical order.
                if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
                    order.setTotalAmount(amount);
                }
            } else if (labelText.contains("配送料・手数料")) {
                order.setShippingFee(amount);
            } else if (labelText.contains("割引") || labelText.contains("プロモーション")) {
                BigDecimal current = order.getDiscounts() != null ? order.getDiscounts() : BigDecimal.ZERO;
                order.setDiscounts(current.add(amount.abs()));
            } else if (labelText.contains("Amazonギフトカード") || labelText.contains("Amazon商品券")
                    || labelText.contains("Amazonギフト券") || labelText.contains("ギフトカード")
                    || labelText.contains("育児支援券")) {
                order.setGiftCardAmount(amount.abs());
                addPayment(order, "Amazon Gift Card", amount.abs());
                // Digital orders often use just "ポイント:" or "ポイント" instead of "Amazonポイント"
            } else if ((labelText.contains("Amazonポイント") && !labelText.contains("手数料"))
                    || labelText.trim().equals("ポイント:") || labelText.trim().equals("ポイント")) {
                order.setPointsUsed(amount.abs());
                addPayment(order, "Amazon Points", amount.abs());
            } else if (labelText.contains("パートナーポイント") || labelText.contains("dポイント")
                    || labelText.contains("PayPayポイント")) {
                order.setDPointsUsed(amount.abs());
                addPayment(order, "Partner Points", amount.abs());
            } else if (labelText.contains("返金額の合計") || labelText.contains("返金合計")) {
                order.setRefundAmount(amount.abs());
            }
        }

        // Fallback: If "ご請求額" is missing (common in digital/subscription orders like
        // Kindle Unlimited),
        // calculate the billed amount from the total amount minus any points/gift cards
        // used.
        if (!hasBilledAmount && order.getTotalAmount() != null) {
            BigDecimal points = order.getPointsUsed() != null ? order.getPointsUsed() : BigDecimal.ZERO;
            BigDecimal giftCards = order.getGiftCardAmount() != null ? order.getGiftCardAmount() : BigDecimal.ZERO;
            BigDecimal dPoints = order.getDPointsUsed() != null ? order.getDPointsUsed() : BigDecimal.ZERO;
            BigDecimal discounts = order.getDiscounts() != null ? order.getDiscounts() : BigDecimal.ZERO;

            BigDecimal billedAmount = order.getTotalAmount().subtract(points).subtract(giftCards).subtract(dPoints)
                    .subtract(discounts);
            order.setBilledAmount(billedAmount);

            // Only add the main payment method if the calculated billed amount is greater
            // than 0
            if (billedAmount.compareTo(BigDecimal.ZERO) > 0) {
                AmazonPayment payment = new AmazonPayment(parseMainPaymentMethod(doc));
                payment.setAmount(billedAmount);
                order.addPayment(payment);
            }
        }
    }

    /**
     * Verifies if the parsed financial numbers add up correctly. Logs a warning
     * if there is a mismatch, indicating potential parsing issues or new page
     * formats.
     *
     * @param order    The parsed {@link AmazonOrder}.
     * @param fileName The name of the source file (for logging).
     * @throws AmazonOrderParseException if the Total amount is missing entirely.
     */
    private void verifyOrderMath(AmazonOrder order, String fileName) {
        if (order.getTotalAmount() == null) {
            throw new AmazonOrderParseException("Cannot verify math: Total amount is missing");
        }

        BigDecimal total = order.getTotalAmount();
        BigDecimal billed = order.getBilledAmount() != null ? order.getBilledAmount() : BigDecimal.ZERO;
        BigDecimal points = order.getPointsUsed() != null ? order.getPointsUsed() : BigDecimal.ZERO;
        BigDecimal giftCards = order.getGiftCardAmount() != null ? order.getGiftCardAmount() : BigDecimal.ZERO;
        BigDecimal dPoints = order.getDPointsUsed() != null ? order.getDPointsUsed() : BigDecimal.ZERO;
        BigDecimal discounts = order.getDiscounts() != null ? order.getDiscounts() : BigDecimal.ZERO;

        // Formula: Total - Discounts - Points - GiftCards = BilledAmount
        BigDecimal calculatedBilled = total.subtract(discounts).subtract(points).subtract(dPoints).subtract(giftCards);

        if (calculatedBilled.compareTo(billed) != 0) {
            // Ignore refund mismatch cases temporarily or log them specifically if needed
            if (order.getRefundAmount() != null && order.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
                logger.info(
                        "Order math mismatch due to refund for order {} in {}. Total: {}, Calculated Billed: {}, Actual Billed: {}",
                        order.getOrderId(), fileName, total, calculatedBilled, billed);
            } else {
                String errMsg = String.format("Math validation failed! Expected Billed: %s, Parsed Billed: %s. "
                        + "(Total: %s, Discounts: %s, Points: %s, DPoints: %s, GiftCards: %s)",
                        calculatedBilled, billed, total, discounts, points, dPoints, giftCards);
                logger.warn("Validation warning for order {} in {}: {}", order.getOrderId(), fileName, errMsg);
                order.setNeedsReview(true);
                order.setReviewReason(errMsg);
            }
        }
    }

    /**
     * Helper method to add a payment entry to the order.
     *
     * @param order  The {@link AmazonOrder}.
     * @param method The payment method name.
     * @param amount The payment amount.
     */
    private void addPayment(AmazonOrder order, String method, BigDecimal amount) {
        AmazonPayment payment = new AmazonPayment(method);
        payment.setAmount(amount);
        order.addPayment(payment);
    }

    /**
     * Parses the main payment method used for the order.
     *
     * @param doc The parsed HTML document.
     * @return A string describing the payment method (e.g., "Visa 1234").
     */
    private String parseMainPaymentMethod(Document doc) {
        // If the summary contains COD fee, the payment method is definitely COD,
        // even if the actual payment details block is hidden by Amazon.
        Element summary = doc.selectFirst("div[data-component='orderSummary']");
        if (summary != null && summary.text().contains("代引手数料")) {
            return "代金引換";
        }

        // Try to extract from modern Next.js/React structure via data-testid
        Elements instruments = doc.select("[data-testid='payment-instrument']");
        for (Element instrument : instruments) {
            // Check for explicit zero-balance indicators in the modern structure before
            // evaluating skip logic
            Element detailEl = instrument
                    .selectFirst("[data-testid='payment-instrument-feature-detail'], [data-testid='paragraph']");
            if (detailEl != null) {
                String detailText = detailEl.text();
                if (detailText.contains("現在料金は発生していません") || detailText.contains("未払いの請求はありません")) {
                    return "なし (Free)";
                }
            }

            Element nameEl = instrument.selectFirst("[data-testid='payment-instrument-name']");
            if (nameEl != null) {
                String name = nameEl.text().trim();
                // Skip Points and Gift Cards, looking for the main credit card or payment
                // method
                if (!name.contains("ポイント") && !name.contains("ギフト") && !name.contains("Gift") && !name.contains("券")) {
                    Element numberEl = instrument.selectFirst("[data-testid='payment-instrument-number']");
                    if (numberEl != null) {
                        return name + " " + numberEl.text().trim();
                    } else if (name.equals("カード")) {
                        return "Credit Card";
                    }
                    return name;
                }
            }
        }

        // Try to find the payment method block and extract credit card info.
        // Add h5 to selector as some receipts use <h5> for the header.
        Element header = doc.selectFirst("div.od-payment-header, span:containsOwn(支払い方法), h5:containsOwn(支払い方法)");
        if (header != null) {
            Element container = header.closest(".a-box");
            if (container == null) {
                container = header.parent();
            }
            if (container != null) {
                String text = container.text();
                // Match common credit card formats (e.g., Visa••••2280) using the pre-compiled
                // case-insensitive pattern
                Matcher m = CREDIT_CARD_PATTERN.matcher(text);
                if (m.find()) {
                    // Combine the card brand (group 1) and the last 4 digits (group 2) cleanly
                    return m.group(1) + " " + m.group(2);
                }
                if (text.contains("代金引換")) {
                    return "代金引換";
                }
                if (text.contains("コンビニ")) {
                    return "コンビニ・ATM・ネットバンキング・電子マネー払い";
                }
                // Handle 0 yen digital orders (e.g., free Kindle books)
                if (text.contains("現在料金は発生していません") || text.contains("未払いの請求はありません")) {
                    return "なし (Free)";
                }
                // Handle generic "カード" (Card) explicitly, avoiding "ギフトカード" (Gift Card)
                if (text.matches(".*(?<!ギフト)カード.*")) {
                    return "Credit Card";
                }
            }
        }

        // Fallback for modern Next.js/React injected receipts (Shadow DOM / Template)
        // Extract text from all <template> elements which hold the payment text in
        // newer Amazon structures.
        Elements templates = doc.select("template");
        for (Element template : templates) {
            Matcher tm = CREDIT_CARD_PATTERN.matcher(template.text());
            if (tm.find()) {
                return tm.group(1) + " " + tm.group(2);
            }
        }

        return "Credit Card"; // Default value
    }

    /**
     * Extracts the list of items purchased in the order.
     *
     * @param doc The parsed HTML document.
     * @return A list of {@link AmazonItem} objects.
     */
    private List<AmazonItem> parseItems(Document doc) {
        List<AmazonItem> items = new ArrayList<>();
        Elements itemContainers = doc.select("div[data-component='purchasedItems'] .a-fixed-left-grid");

        for (Element container : itemContainers) {
            Element titleLink = container.selectFirst("div[data-component='itemTitle'] a");
            if (titleLink == null || titleLink.text().trim().isBlank()) {
                continue; // Cannot create item without a name
            }

            AmazonItem item = new AmazonItem(titleLink.text().trim());

            String href = titleLink.attr("href");
            if (href != null) {
                Matcher m = ASIN_PATTERN.matcher(href);
                if (m.find()) {
                    item.setItemId(m.group(1));
                }
            }

            // Seller
            Element sellerEl = container.selectFirst("div[data-component='orderedMerchant']");
            if (sellerEl != null) {
                // Text is "販売: Seller Name", so we remove the prefix.
                String sellerText = sellerEl.text()
                        .replace("販売:", "")
                        .trim();
                item.setSeller(sellerText);
            }

            Element priceEl = container.selectFirst("div[data-component='unitPrice'] span.a-offscreen");
            if (priceEl == null) {
                priceEl = container.selectFirst("div[data-component='unitPrice'] span[aria-hidden='true']");
            }
            if (priceEl != null) {
                item.setUnitPrice(parseMoney(priceEl.text()));
            }

            int quantity = 1;
            // Check standard quantity block, and fallback to image overlay quantity
            // indicator (.od-item-view-qty)
            Element qtyEl = container
                    .selectFirst("div[data-component='quantity'] span, .od-item-view-qty span, .od-item-view-qty");
            if (qtyEl != null) {
                String qtyText = qtyEl.text().replaceAll("[^0-9]", "");
                if (!qtyText.isEmpty()) {
                    try {
                        quantity = Integer.parseInt(qtyText);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            item.setQuantity(quantity);

            items.add(item);
        }
        return items;
    }

    /**
     * Parses a string containing money into a {@link BigDecimal}.
     *
     * @param text The text containing the money value (e.g., "￥ 1,200").
     * @return The parsed {@link BigDecimal}, or ZERO if invalid.
     */
    private BigDecimal parseMoney(String text) {
        if (text == null) {
            return BigDecimal.ZERO;
        }
        String clean = text.replaceAll("[^0-9.-]", "");
        if (clean.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse money value: {}", text);
            return BigDecimal.ZERO;
        }
    }
}
