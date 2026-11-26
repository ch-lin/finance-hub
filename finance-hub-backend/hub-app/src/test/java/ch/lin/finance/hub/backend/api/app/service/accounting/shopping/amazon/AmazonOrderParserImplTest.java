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
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import ch.lin.finance.hub.backend.api.common.exception.AmazonOrderParseException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

public class AmazonOrderParserImplTest {

    private AmazonOrderParser amazonOrderParser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        amazonOrderParser = new AmazonOrderParserImpl();
    }

    private File getFile(String path) throws FileNotFoundException {
        return ResourceUtils.getFile("classpath:" + path);
    }

    @Test
    @DisplayName("解析包含禮品卡支付的訂單應成功")
    void parse_orderWithGiftCard_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/amazon/order-gift-card.html");

        AmazonOrder order = amazonOrderParser.parse(testFile);

        assertAll("驗證訂單資訊",
                () -> assertEquals("503-3525426-7759011", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2026, 3, 9), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("5208").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("2208").compareTo(order.getBilledAmount())),
                () -> {
                    assertNotNull(order.getGiftCardAmount(), "Gift card amount should not be null");
                    assertEquals(0, new BigDecimal("3000").compareTo(order.getGiftCardAmount()));
                },
                () -> {
                    BigDecimal discounts = order.getDiscounts() == null ? BigDecimal.ZERO
                            : order.getDiscounts();
                    assertEquals(0, BigDecimal.ZERO.compareTo(discounts));
                },
                () -> assertEquals(2, order.getItems().size(), "應有 2 個項目"));

        AmazonItem item1 = order.getItems().stream().filter(i -> "B0DHZMJ6WB".equals(i.getItemId())).findFirst()
                .orElse(null);
        assertNotNull(item1);
        assertEquals("Amazon.co.jp", item1.getSeller());
        assertEquals(0, new BigDecimal("3209").compareTo(item1.getUnitPrice()));

        AmazonItem item2 = order.getItems().stream().filter(i -> "B0DGV39BCF".equals(i.getItemId())).findFirst()
                .orElse(null);
        assertNotNull(item2);
        assertEquals("ACCURTEK公式ストア", item2.getSeller());
        assertEquals(0, new BigDecimal("1999").compareTo(item2.getUnitPrice()));
    }

    @Test
    @DisplayName("解析包含折扣和合作夥伴點數的訂單應成功")
    void parse_orderWithDiscountAndPartnerPoints_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/amazon/order-discount-points.html");

        AmazonOrder order = amazonOrderParser.parse(testFile);

        assertAll("驗證訂單資訊",
                () -> assertEquals("503-3225941-6003843", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2026, 3, 9), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("5179").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("5034").compareTo(order.getBilledAmount())),
                () -> assertEquals(0, new BigDecimal("100").compareTo(order.getDiscounts())),
                () -> assertEquals(0, new BigDecimal("45").compareTo(order.getDPointsUsed())),
                () -> assertEquals(2, order.getItems().size()));
    }

    @Test
    @DisplayName("解析包含 Amazon 點數和合作夥伴點數的訂單應成功")
    void parse_orderWithAmazonAndPartnerPoints_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/amazon/order-amazon-points.html");

        AmazonOrder order = amazonOrderParser.parse(testFile);

        assertAll("驗證訂單資訊",
                () -> assertEquals("250-0690647-5323808", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2026, 1, 7), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("7550").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("6826").compareTo(order.getBilledAmount())),
                () -> assertEquals(0, new BigDecimal("604").compareTo(order.getPointsUsed())),
                () -> assertEquals(0, new BigDecimal("120").compareTo(order.getDPointsUsed())),
                () -> assertEquals(1, order.getItems().size()));
    }

    @Test
    @DisplayName("解析缺少訂單 ID 的檔案應拋出例外")
    void parse_fileWithMissingOrderId_throwsException() throws Exception {
        File testFile = getFile("mock-data/shopping/amazon/invalid-order-no-id.html");

        Exception exception = assertThrows(AmazonOrderParseException.class,
                () -> amazonOrderParser.parse(testFile));
        assertTrue(exception.getMessage().contains("Missing mandatory field: Order ID"));
    }

    @Test
    @DisplayName("解析不存在的檔案應拋出例外")
    void parse_nonExistentFile_throwsException() {
        File nonExistentFile = new File("non/existent/path/file.html");

        Exception exception = assertThrows(AmazonOrderParseException.class,
                () -> amazonOrderParser.parse(nonExistentFile));
        assertTrue(exception.getMessage().contains("File read error"));
    }

    @Test
    @DisplayName("解析真實檔案 summary1.html 應成功")
    void parse_realFile_summary1_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/amazon/summary1.html");

        AmazonOrder order = amazonOrderParser.parse(testFile);

        assertAll("驗證訂單資訊",
                () -> assertEquals("503-3525426-7759011", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2026, 3, 9), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("5208").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("2208").compareTo(order.getBilledAmount())),
                () -> {
                    assertNotNull(order.getGiftCardAmount(), "Gift card amount should not be null");
                    assertEquals(0, new BigDecimal("3000").compareTo(order.getGiftCardAmount()));
                },
                () -> {
                    BigDecimal discounts = order.getDiscounts() == null ? BigDecimal.ZERO
                            : order.getDiscounts();
                    assertEquals(0, BigDecimal.ZERO.compareTo(discounts));
                },
                () -> assertEquals(2, order.getItems().size()));

        AmazonItem item1 = order.getItems().stream().filter(i -> "B0DHZMJ6WB".equals(i.getItemId())).findFirst()
                .orElse(null);
        assertNotNull(item1);
        assertEquals("Amazon.co.jp", item1.getSeller());
        assertEquals(0, new BigDecimal("3209").compareTo(item1.getUnitPrice()));

        AmazonItem item2 = order.getItems().stream().filter(i -> "B0DGV39BCF".equals(i.getItemId())).findFirst()
                .orElse(null);
        assertNotNull(item2);
        assertEquals("ACCURTEK公式ストア", item2.getSeller());
        assertEquals(0, new BigDecimal("1999").compareTo(item2.getUnitPrice()));
    }

    @Test
    @DisplayName("解析真實檔案 summary2.html 應成功")
    void parse_realFile_summary2_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/amazon/summary2.html");

        AmazonOrder order = amazonOrderParser.parse(testFile);

        assertAll("驗證訂單資訊",
                () -> assertEquals("503-3225941-6003843", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2026, 3, 9), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("5179").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("5034").compareTo(order.getBilledAmount())),
                () -> assertEquals(0, new BigDecimal("100").compareTo(order.getDiscounts())),
                () -> assertEquals(0, new BigDecimal("45").compareTo(order.getDPointsUsed())),
                () -> assertEquals(2, order.getItems().size()));

        AmazonItem item1 = order.getItems().stream().filter(i -> "B09KCFPMS3".equals(i.getItemId())).findFirst()
                .orElse(null);
        assertNotNull(item1);
        assertEquals("Amazon.co.jp", item1.getSeller());
        assertEquals(0, new BigDecimal("3180").compareTo(item1.getUnitPrice()));

        AmazonItem item2 = order.getItems().stream().filter(i -> "B0DGV39BCF".equals(i.getItemId())).findFirst()
                .orElse(null);
        assertNotNull(item2);
        assertEquals("ACCURTEK公式ストア", item2.getSeller());
        assertEquals(0, new BigDecimal("1999").compareTo(item2.getUnitPrice()));
    }

    @Test
    @DisplayName("解析真實檔案 summary3.html 應成功")
    void parse_realFile_summary3_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/amazon/summary3.html");

        AmazonOrder order = amazonOrderParser.parse(testFile);

        assertAll("驗證訂單資訊",
                () -> assertEquals("250-0690647-5323808", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2026, 1, 7), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("7550").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("6826").compareTo(order.getBilledAmount())),
                () -> assertEquals(0, new BigDecimal("604").compareTo(order.getPointsUsed())),
                () -> assertEquals(0, new BigDecimal("120").compareTo(order.getDPointsUsed())),
                () -> {
                    BigDecimal giftCard = order.getGiftCardAmount() == null ? BigDecimal.ZERO
                            : order.getGiftCardAmount();
                    assertEquals(0, BigDecimal.ZERO.compareTo(giftCard));
                },
                () -> {
                    BigDecimal discounts = order.getDiscounts() == null ? BigDecimal.ZERO
                            : order.getDiscounts();
                    assertEquals(0, BigDecimal.ZERO.compareTo(discounts));
                },
                () -> assertEquals(1, order.getItems().size()));

        AmazonItem item1 = order.getItems().stream().filter(i -> "B0D3586MWW".equals(i.getItemId())).findFirst()
                .orElse(null);
        assertNotNull(item1);
        assertEquals("おしゃれインテリアのAmplimle", item1.getSeller());
        assertEquals(0, new BigDecimal("7550").compareTo(item1.getUnitPrice()));
    }

    private File createTempHtml(String htmlContent) throws Exception {
        File tempFile = File.createTempFile("amazon-test", ".html");
        tempFile.deleteOnExit();
        java.nio.file.Files.writeString(tempFile.toPath(), htmlContent);
        return tempFile;
    }

    @Test
    @DisplayName("Test branch coverage: getTotalAmount is not null and not zero")
    void parse_orderSummary_totalAmountNotNullAndNotZero() throws Exception {
        String html = "<html><body>" +
                "<div data-component='orderId'> <span>123-4567890-1234567</span> </div>" +
                "<div data-component='orderDate'> <span>2026年3月9日</span> </div>" +
                "<div data-component='orderSummary'>" +
                "  <div class='od-line-item-row'>" +
                "    <span class='od-line-item-row-label'>商品の小計:</span>" +
                "    <span class='od-line-item-row-content'><span>￥1,000</span></span>" +
                "  </div>" +
                "  <div class='od-line-item-row'>" +
                "    <span class='od-line-item-row-label'>この注文の合計:</span>" +
                "    <span class='od-line-item-row-content'><span>￥500</span></span>" +
                "  </div>" +
                "</div>" +
                "<div data-testid='payment-instrument'>Visa</div>" +
                "</body></html>";
        File tempFile = createTempHtml(html);
        AmazonOrder order = amazonOrderParser.parse(tempFile);

        // total amount should still be 1000, not overwritten by 500
        assertEquals(0, new BigDecimal("1000").compareTo(order.getTotalAmount()));
        assertEquals(0, new BigDecimal("500").compareTo(order.getBilledAmount()));
    }

    @Test
    @DisplayName("Test branch coverage: getTotalAmount is zero")
    void parse_orderSummary_totalAmountIsZero() throws Exception {
        String html = "<html><body>" +
                "<div data-component='orderId'> <span>123-4567890-1234568</span> </div>" +
                "<div data-component='orderDate'> <span>2026年3月9日</span> </div>" +
                "<div data-component='orderSummary'>" +
                "  <div class='od-line-item-row'>" +
                "    <span class='od-line-item-row-label'>商品の小計:</span>" +
                "    <span class='od-line-item-row-content'><span>￥0</span></span>" +
                "  </div>" +
                "  <div class='od-line-item-row'>" +
                "    <span class='od-line-item-row-label'>この注文の合計:</span>" +
                "    <span class='od-line-item-row-content'><span>￥0</span></span>" +
                "  </div>" +
                "</div>" +
                "<div data-testid='payment-instrument'>Visa</div>" +
                "</body></html>";
        File tempFile = createTempHtml(html);
        AmazonOrder order = amazonOrderParser.parse(tempFile);

        // total amount should be updated by この注文の合計 since it was zero
        assertEquals(0, new BigDecimal("0").compareTo(order.getTotalAmount()));
        assertEquals(0, new BigDecimal("0").compareTo(order.getBilledAmount()));
    }

    @Test
    @DisplayName("Test branch coverage: getTotalAmount is null (future-proofing via reflection)")
    void parse_orderSummary_totalAmountIsNull_viaReflection() throws Exception {
        // Since AmazonOrder initializes totalAmount to BigDecimal.ZERO, we cannot reach
        // the 'null' branch
        // through normal parsing. We use reflection to directly test the private method
        // handling null.

        java.lang.reflect.Method parseOrderSummaryMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseOrderSummary", org.jsoup.nodes.Document.class, AmazonOrder.class);
        parseOrderSummaryMethod.setAccessible(true);
        AmazonOrder order = new AmazonOrder("test-id", java.time.LocalDate.now());

        // Test 1: 商品の小計 (subtotal) branch
        String htmlSubtotal = "<html><body><div data-component='orderSummary'>" +
                "  <div class='od-line-item-row'>" +
                "    <span class='od-line-item-row-label'>商品の小計:</span>" +
                "    <span class='od-line-item-row-content'><span>￥500</span></span>" +
                "  </div>" +
                "</div></body></html>";

        order.setTotalAmount(null);
        parseOrderSummaryMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlSubtotal), order);
        assertEquals(0, new BigDecimal("500").compareTo(order.getTotalAmount()));

        // Test 2: この注文の合計 (total) branch
        String htmlTotal = "<html><body><div data-component='orderSummary'>" +
                "  <div class='od-line-item-row'>" +
                "    <span class='od-line-item-row-label'>この注文の合計:</span>" +
                "    <span class='od-line-item-row-content'><span>￥800</span></span>" +
                "  </div>" +
                "</div></body></html>";

        order.setTotalAmount(null);
        parseOrderSummaryMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlTotal), order);
        assertEquals(0, new BigDecimal("800").compareTo(order.getTotalAmount()));
    }

    @Test
    @DisplayName("Test branch coverage: getDiscounts() != null (future-proofing via reflection)")
    void parse_orderSummary_discounts_viaReflection() throws Exception {
        java.lang.reflect.Method parseOrderSummaryMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseOrderSummary", org.jsoup.nodes.Document.class, AmazonOrder.class);
        parseOrderSummaryMethod.setAccessible(true);
        AmazonOrder order = new AmazonOrder("test-id", java.time.LocalDate.now());

        // L301: getDiscounts() == null and != null
        String htmlDiscount = "<html><body><div data-component='orderSummary'>" +
                "  <div class='od-line-item-row'>" +
                "    <span class='od-line-item-row-label'>割引:</span>" +
                "    <span class='od-line-item-row-content'><span>-￥10</span></span>" +
                "  </div>" +
                "</div></body></html>";

        // First invoke: order.getDiscounts() is null
        order.setDiscounts(null);
        parseOrderSummaryMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlDiscount), order);
        assertEquals(0, new BigDecimal("10").compareTo(order.getDiscounts()));

        // Second invoke: order.getDiscounts() is not null
        parseOrderSummaryMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlDiscount), order);
        assertEquals(0, new BigDecimal("20").compareTo(order.getDiscounts()));
    }

    @Test
    @DisplayName("Test branch coverage: deduction fields != null in parseOrderSummary (future-proofing via reflection)")
    void parse_orderSummary_deductions_viaReflection() throws Exception {
        java.lang.reflect.Method parseOrderSummaryMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseOrderSummary", org.jsoup.nodes.Document.class, AmazonOrder.class);
        parseOrderSummaryMethod.setAccessible(true);
        AmazonOrder order = new AmazonOrder("test-id", java.time.LocalDate.now());

        // Setup initial deductions
        order.setTotalAmount(new BigDecimal("100"));
        order.setPointsUsed(new BigDecimal("5"));
        order.setGiftCardAmount(new BigDecimal("5"));
        order.setDPointsUsed(new BigDecimal("5"));
        order.setDiscounts(new BigDecimal("5"));

        // Dummy document to trigger the block at the end of parseOrderSummary
        String htmlDummy = "<html><body><div data-component='orderSummary'></div></body></html>";

        // Test == null branches
        AmazonOrder orderNull = new AmazonOrder("test-id", java.time.LocalDate.now());
        orderNull.setTotalAmount(new BigDecimal("100"));
        orderNull.setBilledAmount(null);
        orderNull.setPointsUsed(null);
        orderNull.setGiftCardAmount(null);
        orderNull.setDPointsUsed(null);
        orderNull.setDiscounts(null);
        parseOrderSummaryMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlDummy), orderNull);
        assertEquals(0, new BigDecimal("100").compareTo(orderNull.getBilledAmount()));

        // Test != null branches
        parseOrderSummaryMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlDummy), order);

        // Billed amount should be calculated based on the non-null deductions
        assertEquals(0, new BigDecimal("80").compareTo(order.getBilledAmount()));
    }

    @Test
    @DisplayName("Test branch coverage: fields != null in verifyOrderMath (future-proofing via reflection)")
    void verifyOrderMath_nullFields_viaReflection() throws Exception {
        java.lang.reflect.Method verifyOrderMathMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "verifyOrderMath", AmazonOrder.class, String.class);
        verifyOrderMathMethod.setAccessible(true);
        AmazonOrder order = new AmazonOrder("test-id", java.time.LocalDate.now());

        // L364-L368: Test when all are null
        order.setTotalAmount(BigDecimal.ZERO);
        order.setBilledAmount(null);
        order.setPointsUsed(null);
        order.setGiftCardAmount(null);
        order.setDPointsUsed(null);
        order.setDiscounts(null);
        verifyOrderMathMethod.invoke(amazonOrderParser, order, "test.html");
        // Verify math matches because 0 == 0
        assertEquals(Boolean.FALSE, order.getNeedsReview());

        // Test when all are non-null
        AmazonOrder orderNonNull = new AmazonOrder("test-id", java.time.LocalDate.now());
        orderNonNull.setTotalAmount(new BigDecimal("100"));
        orderNonNull.setBilledAmount(new BigDecimal("100"));
        orderNonNull.setPointsUsed(new BigDecimal("10"));
        orderNonNull.setGiftCardAmount(new BigDecimal("20"));
        orderNonNull.setDPointsUsed(new BigDecimal("30"));
        orderNonNull.setDiscounts(new BigDecimal("40"));

        verifyOrderMathMethod.invoke(amazonOrderParser, orderNonNull, "test.html");
        // 100 != 100 + 10 + 20 + 30 + 40 (200) -> Needs review
        assertEquals(Boolean.TRUE, orderNonNull.getNeedsReview());

        // Test refundAmount branches (L375)
        AmazonOrder orderRefund = new AmazonOrder("test-id", java.time.LocalDate.now());
        orderRefund.setTotalAmount(new BigDecimal("100"));
        orderRefund.setBilledAmount(new BigDecimal("50")); // Forces calculatedBilled != billed

        // Branch 1: refundAmount == null
        orderRefund.setRefundAmount(null);
        verifyOrderMathMethod.invoke(amazonOrderParser, orderRefund, "test.html");

        // Branch 2: refundAmount != null, but <= 0
        orderRefund.setRefundAmount(BigDecimal.ZERO);
        verifyOrderMathMethod.invoke(amazonOrderParser, orderRefund, "test.html");

        // Branch 3: refundAmount > 0
        orderRefund.setRefundAmount(new BigDecimal("10"));
        verifyOrderMathMethod.invoke(amazonOrderParser, orderRefund, "test.html");
    }

    @Test
    @DisplayName("Test branch coverage: parseMainPaymentMethod via reflection")
    void parseMainPaymentMethod_viaReflection() throws Exception {
        java.lang.reflect.Method parseMainPaymentMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseMainPaymentMethod", org.jsoup.nodes.Document.class);
        parseMainPaymentMethod.setAccessible(true);

        // Branch: summary == null
        String htmlNoSummary = "<html><body></body></html>";
        parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlNoSummary));

        // Branch: summary != null, contains 代引手数料
        String htmlCod = "<html><body><div data-component='orderSummary'>代引手数料</div></body></html>";
        String result = (String) parseMainPaymentMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlCod));
        assertEquals("代金引換", result);

        // Branch: L426 detailText contains 現在料金は発生していません
        String htmlFree1 = "<html><body><div data-testid='payment-instrument'>" +
                "<div data-testid='payment-instrument-feature-detail'>現在料金は発生していません</div>" +
                "</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlFree1));
        assertEquals("なし (Free)", result);

        // Branch: L426 detailText contains 未払いの請求はありません
        String htmlFree2 = "<html><body><div data-testid='payment-instrument'>" +
                "<div data-testid='payment-instrument-feature-detail'>未払いの請求はありません</div>" +
                "</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlFree2));
        assertEquals("なし (Free)", result);

        // Branch: L426 detailText contains neither, summary != null but no cod
        String htmlFree3 = "<html><body><div data-component='orderSummary'>no cod</div>" +
                "<div data-testid='payment-instrument'>" +
                "<div data-testid='payment-instrument-feature-detail'>something else</div>" +
                "<div data-testid='payment-instrument-name'>Visa</div>" +
                "<div data-testid='payment-instrument-number'>1234</div>" +
                "</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlFree3));
        assertEquals("Visa 1234", result);

        // Branch: L436 names to skip
        String[] skipNames = { "ポイント", "ギフト", "Gift", "券" };
        for (String skipName : skipNames) {
            String htmlSkip = "<html><body><div data-testid='payment-instrument'>" +
                    "<div data-testid='payment-instrument-name'>" + skipName + "</div>" +
                    "</div></body></html>";
            parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlSkip));
        }

        // Branch: L440 name equals カード
        String htmlCard = "<html><body><div data-testid='payment-instrument'>" +
                "<div data-testid='payment-instrument-name'>カード</div>" +
                "</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlCard));
        assertEquals("Credit Card", result);

        // Branch: L443 name fallback
        String htmlNameOnly = "<html><body><div data-testid='payment-instrument'>" +
                "<div data-testid='payment-instrument-name'>OtherMethod</div>" +
                "</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlNameOnly));
        assertEquals("OtherMethod", result);

        // --- Fallback DOM parsing ---
        // L453: container == null -> uses header.parent()
        // L461: Credit card match
        String htmlHeaderParentCc = "<html><body>" +
                "<div><h5 class='od-payment-header'>支払い方法</h5>Visa ending in 4321</div>" +
                "</body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlHeaderParentCc));
        assertEquals("Visa 4321", result);

        // L468: コンビニ
        String htmlHeaderConbini = "<html><body><div class='a-box'>" +
                "<div class='od-payment-header'>支払い方法</div>コンビニ</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlHeaderConbini));
        assertEquals("コンビニ・ATM・ネットバンキング・電子マネー払い", result);

        // L472: 現在料金は発生していません
        String htmlHeaderFree = "<html><body><div class='a-box'>" +
                "<div class='od-payment-header'>支払い方法</div>現在料金は発生していません</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlHeaderFree));
        assertEquals("なし (Free)", result);

        // L472: 未払いの請求はありません
        String htmlHeaderFree2 = "<html><body><div class='a-box'>" +
                "<div class='od-payment-header'>支払い方法</div>未払いの請求はありません</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlHeaderFree2));
        assertEquals("なし (Free)", result);

        // L476: カード (not ギフトカード)
        String htmlHeaderCard = "<html><body><div class='a-box'>" +
                "<div class='od-payment-header'>支払い方法</div>JCBカード</div></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlHeaderCard));
        assertEquals("Credit Card", result);

        // Fallback: None of the above match
        String htmlHeaderOther = "<html><body><div class='a-box'>" +
                "<div class='od-payment-header'>支払い方法</div>銀行振込</div></body></html>";
        parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlHeaderOther));

        // L488: Template fallback
        String htmlTemplateCc = "<html><body><template>Mastercard ending in 9999</template></body></html>";
        result = (String) parseMainPaymentMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlTemplateCc));
        assertEquals("Mastercard 9999", result);

        // L488: Template fallback does not match
        String htmlTemplateOther = "<html><body><template>Some other text</template></body></html>";
        parseMainPaymentMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlTemplateOther));
    }

    @Test
    @DisplayName("Test L456 branch coverage: container == null via Mockito")
    void parseMainPaymentMethod_containerNull_viaMockito() throws Exception {
        java.lang.reflect.Method parseMainPaymentMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseMainPaymentMethod", org.jsoup.nodes.Document.class);
        parseMainPaymentMethod.setAccessible(true);

        org.jsoup.nodes.Document mockDoc = org.mockito.Mockito.mock(org.jsoup.nodes.Document.class);
        org.jsoup.nodes.Element mockHeader = org.mockito.Mockito.mock(org.jsoup.nodes.Element.class);
        org.mockito.Mockito
                .when(mockDoc.selectFirst(
                        "div.od-payment-header, span:containsOwn(支払い方法), h5:containsOwn(支払い方法)"))
                .thenReturn(mockHeader);
        org.mockito.Mockito.when(mockHeader.closest(".a-box")).thenReturn(null);
        org.mockito.Mockito.when(mockHeader.parent()).thenReturn(null); // This causes container to be null!

        // Let other selects return empty
        org.mockito.Mockito.when(mockDoc.select(org.mockito.Mockito.anyString()))
                .thenReturn(new org.jsoup.select.Elements());

        parseMainPaymentMethod.invoke(amazonOrderParser, mockDoc);
    }

    @Test
    @DisplayName("Test branch coverage: parseItems and parseMoney via reflection")
    void parseItems_viaReflection() throws Exception {
        java.lang.reflect.Method parseItemsMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseItems", org.jsoup.nodes.Document.class);
        parseItemsMethod.setAccessible(true);

        java.lang.reflect.Method parseMoneyMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseMoney", String.class);
        parseMoneyMethod.setAccessible(true);

        // L568: parseMoney null
        assertEquals(BigDecimal.ZERO, parseMoneyMethod.invoke(amazonOrderParser, (String) null));

        // L577: parseMoney throws NumberFormatException
        assertEquals(BigDecimal.ZERO, parseMoneyMethod.invoke(amazonOrderParser, "1.2.3.4"));
        assertEquals(BigDecimal.ZERO, parseMoneyMethod.invoke(amazonOrderParser, "-"));

        // L508: titleLink == null
        String htmlNoTitleLink = "<html><body><div data-component='purchasedItems'>" +
                "<div class='a-fixed-left-grid'></div></div></body></html>";
        List<?> items = (List<?>) parseItemsMethod.invoke(amazonOrderParser,
                org.jsoup.Jsoup.parse(htmlNoTitleLink));
        assertEquals(0, items.size());

        // L508: titleLink blank
        String htmlBlankTitleLink = "<html><body><div data-component='purchasedItems'>" +
                "<div class='a-fixed-left-grid'><div data-component='itemTitle'><a>  </a></div></div></div></body></html>";
        items = (List<?>) parseItemsMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlBlankTitleLink));
        assertEquals(0, items.size());

        // Normal title, test L515 (no href), L524 (no seller), L536 (no price), L547
        // (qty empty/invalid)
        String htmlMissingFields = "<html><body><div data-component='purchasedItems'>" +
                "<div class='a-fixed-left-grid'>" +
                "<div data-component='itemTitle'><a>Item</a></div>" +
                "<div data-component='quantity'><span></span></div>" +
                "</div></div></body></html>";
        items = (List<?>) parseItemsMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlMissingFields));
        assertEquals(1, items.size());
        assertEquals("Item",
                ((ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem) items
                        .get(0))
                        .getName());
        assertEquals(1,
                ((ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem) items
                        .get(0))
                        .getQuantity()); // Default 1

        // Normal title, invalid ASIN
        String htmlInvalidAsin = "<html><body><div data-component='purchasedItems'>" +
                "<div class='a-fixed-left-grid'>" +
                "<div data-component='itemTitle'><a href='/dp/INV'>Item</a></div>" +
                "</div></div></body></html>";
        items = (List<?>) parseItemsMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlInvalidAsin));
        assertEquals(1, items.size());
        assertEquals(null,
                ((ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem) items
                        .get(0))
                        .getItemId());

        // L550: catch NumberFormatException for quantity
        String htmlHugeQty = "<html><body><div data-component='purchasedItems'>" +
                "<div class='a-fixed-left-grid'>" +
                "<div data-component='itemTitle'><a>Item</a></div>" +
                "<div data-component='quantity'><span>999999999999999999</span></div>" +
                "</div></div></body></html>";
        items = (List<?>) parseItemsMethod.invoke(amazonOrderParser, org.jsoup.Jsoup.parse(htmlHugeQty));
        assertEquals(1, items.size());
        assertEquals(1,
                ((ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem) items
                        .get(0))
                        .getQuantity()); // Default 1
    }

    @Test
    @DisplayName("Test L515 branch coverage: href == null via Mockito")
    void parseItems_hrefNull_viaMockito() throws Exception {
        java.lang.reflect.Method parseItemsMethod = AmazonOrderParserImpl.class.getDeclaredMethod(
                "parseItems", org.jsoup.nodes.Document.class);
        parseItemsMethod.setAccessible(true);

        org.jsoup.nodes.Document mockDoc = org.mockito.Mockito.mock(org.jsoup.nodes.Document.class);
        org.jsoup.nodes.Element mockContainer = org.mockito.Mockito.mock(org.jsoup.nodes.Element.class);
        org.jsoup.nodes.Element mockTitleLink = org.mockito.Mockito.mock(org.jsoup.nodes.Element.class);

        org.jsoup.select.Elements itemContainers = new org.jsoup.select.Elements(mockContainer);
        org.mockito.Mockito.when(mockDoc.select("div[data-component='purchasedItems'] .a-fixed-left-grid"))
                .thenReturn(itemContainers);

        org.mockito.Mockito.when(mockContainer.selectFirst("div[data-component='itemTitle'] a"))
                .thenReturn(mockTitleLink);
        org.mockito.Mockito.when(mockTitleLink.text()).thenReturn("Item Name");
        org.mockito.Mockito.when(mockTitleLink.attr("href")).thenReturn(null); // This causes href to be null!

        parseItemsMethod.invoke(amazonOrderParser, mockDoc);
    }
}
