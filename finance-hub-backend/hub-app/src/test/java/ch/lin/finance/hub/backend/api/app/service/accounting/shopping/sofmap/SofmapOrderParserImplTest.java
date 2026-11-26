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
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import ch.lin.finance.hub.backend.api.common.exception.SofmapOrderParseException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;

public class SofmapOrderParserImplTest {

    private SofmapOrderParser sofmapOrderParser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        sofmapOrderParser = new SofmapOrderParserImpl();
    }

    private File getFile(String path) throws FileNotFoundException {
        return ResourceUtils.getFile("classpath:" + path);
    }

    @Test
    @DisplayName("Parse file 046174418 - has points used")
    void parse_realFile_046174418_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/sofmap/お取引の詳細 _ sofmap.com - 046174418.html");
        SofmapOrder order = sofmapOrderParser.parse(testFile);

        assertAll("Verify Order 046174418",
                () -> assertEquals("046174418", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2016, 2, 15), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("22074").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("22000").compareTo(order.getBilledAmount())),
                () -> assertEquals(0, new BigDecimal("74").compareTo(order.getPointsUsed())),
                () -> assertEquals(0, new BigDecimal("222").compareTo(order.getEarnedPoints())),
                () -> assertEquals(0, new BigDecimal("0").compareTo(order.getShippingFee())),
                () -> assertTrue(!order.getItems().isEmpty()),
                () -> assertEquals(1, order.getPayments().size()),
                () -> assertEquals(1, order.getShipments().size()));
    }

    @Test
    @DisplayName("Parse file 043396653 - no points used")
    void parse_realFile_043396653_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/sofmap/お取引の詳細 _ sofmap.com - 043396653.html");
        SofmapOrder order = sofmapOrderParser.parse(testFile);

        assertAll("Verify Order 043396653",
                () -> assertEquals("043396653", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2014, 7, 26), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("9913").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("9913").compareTo(order.getBilledAmount())),
                () -> {
                    BigDecimal points = order.getPointsUsed() == null ? BigDecimal.ZERO : order.getPointsUsed();
                    assertEquals(0, BigDecimal.ZERO.compareTo(points));
                },
                () -> assertEquals(0, new BigDecimal("0").compareTo(order.getEarnedPoints())));
    }

    @Test
    @DisplayName("Parse file 046227641 - has points used")
    void parse_realFile_046227641_succeeds() throws Exception {
        File testFile = getFile("mock-data/shopping/sofmap/お取引の詳細 _ sofmap.com - 046227641.html");
        SofmapOrder order = sofmapOrderParser.parse(testFile);

        assertAll("Verify Order 046227641",
                () -> assertEquals("046227641", order.getOrderId()),
                () -> assertEquals(LocalDate.of(2016, 3, 30), order.getOrderDate()),
                () -> assertEquals(0, new BigDecimal("18554").compareTo(order.getTotalAmount())),
                () -> assertEquals(0, new BigDecimal("18332").compareTo(order.getBilledAmount())),
                () -> assertEquals(0, new BigDecimal("222").compareTo(order.getPointsUsed())),
                () -> assertEquals(0, new BigDecimal("185").compareTo(order.getEarnedPoints())));
    }

    @Test
    @DisplayName("Parse missing Order ID throws exception")
    void parse_missingOrderId_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文日</dt><dd>2016年2月15日</dd></dl>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Order ID"));
    }

    @Test
    @DisplayName("Parse missing Order Date throws exception")
    void parse_missingOrderDate_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文番号</dt><dd>046174418</dd></dl>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Order Date"));
    }

    @Test
    @DisplayName("Branch coverage: blank Order ID throws exception")
    void parse_blankOrderId_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文番号</dt><dd>  </dd><dt>ご注文日</dt><dd>2016年2月15日</dd></dl>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Order ID"));
    }

    @Test
    @DisplayName("Branch coverage: missing Total Amount throws exception")
    void parse_missingTotalAmount_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文番号</dt><dd>046174418</dd><dt>ご注文日</dt><dd>2016年2月15日</dd></dl>" +
                "<table><tbody><tr><th>Some other field</th><td>1000</td></tr></tbody></table>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Total Amount"));
    }

    @Test
    @DisplayName("Branch coverage: Date parsing dt without dd throws exception")
    void parse_missingNextElementSibling_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文番号</dt><dd>046174418</dd><dt>ご注文日</dt></dl>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Order Date"));
    }

    @Test
    @DisplayName("Branch coverage: Order ID dt without dd throws exception")
    void parse_missingOrderIdNextElementSibling_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文日</dt><dd>2016年2月15日</dd><dt>ご注文番号</dt></dl>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Order ID"));
    }

    @Test
    @DisplayName("Branch coverage: Date string without pattern match throws exception")
    void parse_dateNoMatch_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文番号</dt><dd>046174418</dd><dt>ご注文日</dt><dd>NotADateString</dd></dl>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Order Date"));
    }

    @Test
    @DisplayName("Branch coverage: Invalid Date string format throws exception")
    void parse_invalidDateValues_throwsException() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'><dt>ご注文番号</dt><dd>046174418</dd><dt>ご注文日</dt><dd>2016年13月15日</dd></dl>" +
                "</body></html>");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Order Date"));
    }

    @Test
    @DisplayName("Branch coverage: Alternative headers and invalid td and payment/shipment without sibling")
    void parse_alternativeHeadersAndInvalidTd_success() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'>" +
                "<dt>ご注文番号</dt><dd>046174418</dd>" +
                "<dt>ご注文日</dt><dd>2016年2月15日</dd>" +
                "</dl>" +
                "<dl><dt>お支払い方法</dt></dl>" +
                "<dl><dt>出荷状況</dt></dl>" +
                "<table><tbody>" +
                "<tr><th>ご注文金額</th><td>1000</td></tr>" +
                "<tr><th>ご注文金額</th><td>2000</td></tr>" +
                "<tr><th>ご注文合計</th><td>1000</td></tr>" +
                "<tr><th>お支払い金額</th><td>900</td></tr>" +
                "<tr><th>利用ポイント</th><td>100</td></tr>" +
                "<tr><th>送料合計</th><th>Not A TD</th></tr>" +
                "<tr><th>Some Field</th></tr>" +
                "</tbody></table>" +
                "</body></html>");
        SofmapOrder order = sofmapOrderParser.parse(tempFile);

        assertEquals(new BigDecimal("1000"), order.getTotalAmount());
        assertEquals(new BigDecimal("900"), order.getBilledAmount());
        assertEquals(new BigDecimal("100"), order.getPointsUsed());
        assertEquals(0, order.getPayments().size());
        assertEquals(0, order.getShipments().size());
    }

    @Test
    @DisplayName("Branch coverage: Items with missing price/qty dt and dd")
    void parse_itemsMissingFields_success() throws Exception {
        File tempFile = createTempHtml("<html><body>" +
                "<dl class='order_dl_grid'>" +
                "<dt>ご注文番号</dt><dd>046174418</dd>" +
                "<dt>ご注文日</dt><dd>2016年2月15日</dd>" +
                "</dl>" +
                "<table><tbody>" +
                "<tr><th>ご注文合計</th><td>ABC</td></tr>" + // Trigger clean.isBlank()
                "</tbody></table>" +
                "<div class='product_box'>" +
                "  <div class='product_name'>Item 1</div>" +
                "  <dl><dt>販売価格</dt></dl>" + // priceDt != null, next == null
                "  <dl><dt>数量</dt></dl>" + // qtyDt != null, next == null
                "</div>" +
                "<div class='product_box'>" +
                "  <div class='product_name'>Item 2</div>" +
                "  <!-- Missing priceDt and qtyDt completely -->" +
                "</div>" +
                "</body></html>");
        SofmapOrder order = sofmapOrderParser.parse(tempFile);

        assertEquals(2, order.getItems().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getItems().get(0).getUnitPrice()));
        assertEquals(1, order.getItems().get(0).getQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getItems().get(1).getUnitPrice()));
        assertEquals(1, order.getItems().get(1).getQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalAmount()));
    }

    @Test
    @DisplayName("Parse non existent file throws exception")
    void parse_nonExistentFile_throwsException() {
        File nonExistent = new File("invalid_path.html");
        Exception ex = assertThrows(SofmapOrderParseException.class, () -> sofmapOrderParser.parse(nonExistent));
        assertTrue(ex.getMessage().contains("File read error"));
    }

    @Test
    @DisplayName("Branch coverage: Math mismatch sets NeedsReview")
    void parse_mathMismatch_setsNeedsReview() throws Exception {
        String html = "<html><body>" +
                "<dl class='order_dl_grid'>" +
                "<dt>ご注文日</dt><dd>2016年2月15日</dd>" +
                "<dt>ご注文番号</dt><dd>046174418</dd>" +
                "</dl>" +
                "<table><tbody>" +
                "<tr><th>ご注文合計(税込)</th><td>¥1,000</td></tr>" +
                "<tr><th>ソフマップポイント値引</th><td>¥-100</td></tr>" +
                "<tr><th>お支払い総額(税込)</th><td>¥500</td></tr>" + // Intentional mismatch: 1000 - 100 = 900 != 500
                "</tbody></table>" +
                "</body></html>";
        File tempFile = createTempHtml(html);
        SofmapOrder order = sofmapOrderParser.parse(tempFile);

        assertEquals(0, new BigDecimal("1000").compareTo(order.getTotalAmount()));
        assertEquals(0, new BigDecimal("500").compareTo(order.getBilledAmount()));
        assertTrue(order.getNeedsReview());
        assertNotNull(order.getReviewReason());
        assertTrue(order.getReviewReason().contains("Validation warning")
                || order.getReviewReason().contains("Math validation failed"));
    }

    @Test
    @DisplayName("Branch coverage: Missing billed amount falls back to total - points")
    void parse_fallbackBilledAmount() throws Exception {
        String html = "<html><body>" +
                "<dl class='order_dl_grid'>" +
                "<dt>ご注文日</dt><dd>2016年2月15日</dd>" +
                "<dt>ご注文番号</dt><dd>046174418</dd>" +
                "</dl>" +
                "<table><tbody>" +
                "<tr><th>ご注文合計(税込)</th><td>¥1,000</td></tr>" +
                "<tr><th>ソフマップポイント値引</th><td>¥-100</td></tr>" +
                "</tbody></table>" +
                "</body></html>";
        File tempFile = createTempHtml(html);
        SofmapOrder order = sofmapOrderParser.parse(tempFile);

        assertEquals(new BigDecimal("900"), order.getBilledAmount());
    }

    @Test
    @DisplayName("Branch coverage: Parse Items Edge Cases")
    void parse_itemsEdgeCases() throws Exception {
        String html = "<html><body>" +
                "<dl class='order_dl_grid'>" +
                "<dt>ご注文日</dt><dd>2016年2月15日</dd>" +
                "<dt>ご注文番号</dt><dd>046174418</dd>" +
                "</dl>" +
                "<table><tbody>" +
                "<tr><th>ご注文合計(税込)</th><td>¥1,000</td></tr>" +
                "</tbody></table>" +
                "<!-- Item without name -->" +
                "<div class='product_box'></div>" +
                "<!-- Item with empty name -->" +
                "<div class='product_box'><a class='product_name'>  </a></div>" +
                "<!-- Item without price/qty -->" +
                "<div class='product_box'><a class='product_name'>Item1</a></div>" +
                "<!-- Item with invalid qty -->" +
                "<div class='product_box'><a class='product_name'>Item2</a>" +
                "<dl><dt>販売価格</dt><dd>¥500</dd></dl>" +
                "<dl><dt>数量</dt><dd>invalid</dd></dl></div>" +
                "<!-- Item with large qty -->" +
                "<div class='product_box'><a class='product_name'>Item3</a>" +
                "<dl><dt>数量</dt><dd>99999999999999</dd></dl></div>" +
                "</body></html>";
        File tempFile = createTempHtml(html);
        SofmapOrder order = sofmapOrderParser.parse(tempFile);

        assertEquals(3, order.getItems().size());
        assertEquals("Item1", order.getItems().get(0).getName());
        assertEquals(1, order.getItems().get(0).getQuantity()); // Default 1

        assertEquals("Item2", order.getItems().get(1).getName());
        assertEquals(0, new BigDecimal("500").compareTo(order.getItems().get(1).getUnitPrice()));
        assertEquals(1, order.getItems().get(1).getQuantity()); // Default 1 fallback

        assertEquals("Item3", order.getItems().get(2).getName());
        assertEquals(1, order.getItems().get(2).getQuantity()); // Out of bounds -> exception caught -> Default 1
    }

    @Test
    @DisplayName("Branch coverage: Missing payment amount and shipments")
    void parse_missingPaymentShipments() throws Exception {
        String html = "<html><body>" +
                "<dl class='order_dl_grid'>" +
                "<dt>ご注文日</dt><dd>2016年2月15日</dd>" +
                "<dt>ご注文番号</dt><dd>046174418</dd>" +
                "<dt>お支払い方法</dt><dd>クレジットカード</dd>" +
                "</dl>" +
                "<table><tbody>" +
                "<tr><th>ご注文合計(税込)</th><td>¥0</td></tr>" + // Total = 0 -> Billed = 0 -> payment not added
                "</tbody></table>" +
                "</body></html>";
        File tempFile = createTempHtml(html);
        SofmapOrder order = sofmapOrderParser.parse(tempFile);

        assertEquals(0, order.getPayments().size());
        assertEquals(0, order.getShipments().size());
    }

    @Test
    @DisplayName("Branch coverage: Invalid money text")
    void parse_invalidMoney() throws Exception {
        String html = "<html><body>" +
                "<dl class='order_dl_grid'>" +
                "<dt>ご注文日</dt><dd>2016年2月15日</dd>" +
                "<dt>ご注文番号</dt><dd>046174418</dd>" +
                "</dl>" +
                "<table><tbody>" +
                "<tr><th>ご注文合計(税込)</th><td>invalid-money-string</td></tr>" +
                "</tbody></table>" +
                "</body></html>";
        File tempFile = createTempHtml(html);
        SofmapOrder order = sofmapOrderParser.parse(tempFile);

        // "invalid-money-string" stripped of non-[0-9.-] is "-"
        // new BigDecimal("-") throws NumberFormatException, caught, returns ZERO
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalAmount()));
    }

    private File createTempHtml(String content) throws Exception {
        File file = File.createTempFile("sofmap-test", ".html");
        file.deleteOnExit();
        java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.Charset.forName("Shift_JIS"));
        return file;
    }
}
