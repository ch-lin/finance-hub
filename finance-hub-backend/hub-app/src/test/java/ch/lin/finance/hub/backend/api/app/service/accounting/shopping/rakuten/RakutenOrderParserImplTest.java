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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.common.exception.RakutenOrderParseException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;

public class RakutenOrderParserImplTest {

    private RakutenOrderParserImpl rakutenOrderParser;

    @BeforeEach
    public void setUp() {
        rakutenOrderParser = new RakutenOrderParserImpl();
    }

    @Test
    public void testParseRakutenOrder1() throws Exception {
        // Point to the reference HTML
        File htmlFile = new File(
                getClass().getResource("/mock-data/shopping/rakuten/276183-20200402-00075740.html").getFile());
        assertTrue(htmlFile.exists(), "Test HTML file does not exist at " + htmlFile.getAbsolutePath());

        RakutenOrder order = rakutenOrderParser.parse(htmlFile);

        assertNotNull(order);
        assertEquals("276183-20200402-00075740", order.getOrderId());
        assertEquals("2020-04-02", order.getOrderDate().toString());
        assertEquals(new BigDecimal("8686"), order.getTotalAmount());
        assertEquals(new BigDecimal("124"), order.getPointsUsed());
        assertEquals(new BigDecimal("8562"), order.getBilledAmount());
        assertFalse(order.getItems().isEmpty());
        assertFalse(order.getPayments().isEmpty());
        assertEquals("Credit Card", order.getPayments().get(0).getMethod());
    }

    @Test
    public void testParseRakutenOrder2() throws Exception {
        File htmlFile = new File(
                getClass().getResource("/mock-data/shopping/rakuten/278256-20140129-0445599324.html").getFile());
        assertTrue(htmlFile.exists(), "Test HTML file does not exist at " + htmlFile.getAbsolutePath());

        RakutenOrder order = rakutenOrderParser.parse(htmlFile);

        assertNotNull(order);
        assertEquals("278256-20140129-0445599324", order.getOrderId());
        assertEquals("2014-01-29", order.getOrderDate().toString());
        assertFalse(order.getItems().isEmpty());
    }

    @Test
    public void testParse_NullOrNonExistentFile() {
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions
                .assertThrows(IllegalArgumentException.class, () -> {
                    rakutenOrderParser.parse(null);
                });
        assertEquals("HTML file cannot be null and must exist.", exception.getMessage());

        IllegalArgumentException exception2 = org.junit.jupiter.api.Assertions
                .assertThrows(IllegalArgumentException.class, () -> {
                    rakutenOrderParser.parse(new File("non-existent-test-file.html"));
                });
        assertEquals("HTML file cannot be null and must exist.", exception2.getMessage());
    }

    @Test
    public void testParse_FallbackLogic() throws Exception {
        // Create a temporary file with missing fields to trigger fallbacks
        File tempFile = File.createTempFile("rakuten_test_fallback", ".html");
        tempFile.deleteOnExit();

        String htmlContent = "<html><body>"
                + "<script>window.__INITIAL_STATE__ = {invalid json};</script>"
                + "<div>注文番号 123456-20230509-000001</div>" // Valid Order ID
                + "<div>2023年05月09日 ご注文</div>" // Date pattern 2
                + "<div>支払い金額 1,000 円</div>" // Billed amount
                + "<div>代金引換</div>" // Payment method
                + "</body></html>";

        java.nio.file.Files.write(tempFile.toPath(),
                htmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        RakutenOrder order = rakutenOrderParser.parse(tempFile);

        assertNotNull(order);
        assertEquals("123456-20230509-000001", order.getOrderId());
        assertEquals("2023-05-09", order.getOrderDate().toString()); // Fallback Date 2
        assertEquals(new BigDecimal("1000"), order.getBilledAmount());
        assertEquals(new BigDecimal("1000"), order.getTotalAmount()); // Fallback Total to Billed
        assertEquals(BigDecimal.ZERO, order.getPointsUsed()); // Fallback Points

        assertFalse(order.getPayments().isEmpty());
        assertEquals("代金引換", order.getPayments().get(0).getMethod());
    }

    @Test
    public void testParse_MissingOrderId_ThrowsException() throws Exception {
        File tempFile = File.createTempFile("rakuten_missing_order_id", ".html");
        tempFile.deleteOnExit();

        String htmlContent = "<html><body>"
                + "<div>2023年05月09日 ご注文</div>"
                + "<div>支払い金額 1,000 円</div>"
                + "</body></html>";

        java.nio.file.Files.write(tempFile.toPath(),
                htmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        RakutenOrderParseException exception = org.junit.jupiter.api.Assertions
                .assertThrows(RakutenOrderParseException.class, () -> {
                    rakutenOrderParser.parse(tempFile);
                });
        assertEquals("Missing mandatory field: Order ID", exception.getMessage());
    }

    @Test
    public void testParse_PaymentMethodsAndZeroBilled() throws Exception {
        File tempFile1 = File.createTempFile("rakuten_payment_1", ".html");
        tempFile1.deleteOnExit();
        java.nio.file.Files.write(tempFile1.toPath(),
                "<html><body>注文日時 2023/05/09 注文番号 123456-12345678-123456789 支払い金額 1,000 円 コンビニ</body></html>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RakutenOrder order1 = rakutenOrderParser.parse(tempFile1);
        assertEquals("コンビニ・ATM・ネットバンキング・電子マネー払い", order1.getPayments().get(0).getMethod());

        File tempFile2 = File.createTempFile("rakuten_payment_2", ".html");
        tempFile2.deleteOnExit();
        java.nio.file.Files.write(tempFile2.toPath(),
                "<html><body>注文日時 2023/05/09 注文番号 123456-12345678-123456789 支払い金額 1,000 円 銀行振込</body></html>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RakutenOrder order2 = rakutenOrderParser.parse(tempFile2);
        assertEquals("銀行振込", order2.getPayments().get(0).getMethod());

        File tempFile3 = File.createTempFile("rakuten_payment_3", ".html");
        tempFile3.deleteOnExit();
        java.nio.file.Files.write(tempFile3.toPath(),
                "<html><body>注文日時 2023/05/09 注文番号 123456-12345678-123456789 支払い金額 1,000 円 後払い</body></html>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RakutenOrder order3 = rakutenOrderParser.parse(tempFile3);
        assertEquals("後払い決済", order3.getPayments().get(0).getMethod());

        // Zero billed -> no payments
        File tempFile4 = File.createTempFile("rakuten_payment_4", ".html");
        tempFile4.deleteOnExit();
        java.nio.file.Files.write(tempFile4.toPath(),
                "<html><body>注文日時 2023/05/09 注文番号 123456-12345678-123456789</body></html>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RakutenOrder order4 = rakutenOrderParser.parse(tempFile4);
        assertTrue(order4.getPayments().isEmpty());
    }

    @Test
    public void testParse_AdvancedBranches() throws Exception {
        File tempFile = File.createTempFile("rakuten_advanced", ".html");
        tempFile.deleteOnExit();

        String validJson = "{\"orderData\": {"
                + "\"orderStatusObject\": {"
                + "  \"statusHistory\": ["
                + "    {\"name\": \"OPP_ORDER_STATUS_DELIVERED\", \"date\": \"2023/05/10 10:00:00\"},"
                + "    {\"name\": \"OPP_ORDER_STATUS_SHIPPED\", \"date\": \"invalid-date\"}"
                + "  ]"
                + "},"
                + "\"shippingList\": ["
                + "  {"
                + "    \"trackingNumber\": \"1234567890\","
                + "    \"deliveryInfo\": {"
                + "      \"deliveryCarrierName\": \"Yamato\","
                + "      \"deliveryDateTime\": \"01/10\"" // Trigger orderYear++
                + "    }"
                + "  },"
                + "  {"
                + "    \"trackingNumber\": \"0987654321\","
                + "    \"deliveryInfo\": {"
                + "      \"deliveryCarrierName\": \"Sagawa\","
                + "      \"deliveryDateTime\": \"99/99\"" // Trigger Exception in LocalDate.of
                + "    }"
                + "  }"
                + "]"
                + "}}";

        String htmlContent = "<html><body>"
                + "<script>window.__INITIAL_STATE__ = " + validJson + ";</script>"
                + "<div>注文番号 123456-20230509-000001</div>"
                + "<div>2023年5月9日 ご注文</div>" // Date pattern 2 with single digit month and day
                + "<div>"
                + "  <a href=\"http://item.rakuten.co.jp/seller1/item1/\">Item 1</a>"
                + "  <a href=\"http://item.rakuten.co.jp/seller1/item1/\">Item 1</a>" // Duplicate href
                + "  <a href=\"mailto:foo@item.rakuten.co.jp\">Mailto</a>" // Path is null
                + "  <a href=\"http://item.rakuten.co.jp/foo\">Item 2</a>" // parts.length < 3
                + "  <a href=\"http://item.rakuten.co.jp/ foo \">Item 3</a>" // URISyntaxException
                + "</div>"
                + "<div><a href=\"http://item.rakuten.co.jp/seller2/item2/\">Item 4</a></div>" // No
                                                                                               // quantity/price
                                                                                               // found
                                                                                               // (depth
                                                                                               // < 10
                                                                                               // but
                                                                                               // no
                                                                                               // details)
                + "<div>数量 円 <a href=\"http://item.rakuten.co.jp/seller3/item3/\">Item 5</a></div>" // 数量
                                                                                                    // and
                                                                                                    // 円
                                                                                                    // but
                                                                                                    // no
                                                                                                    // price
                                                                                                    // pattern,
                                                                                                    // no
                                                                                                    // qty
                                                                                                    // pattern
                + "<div style='height: 1000px'>" // Add layers to test depth = 10
                + "  <div><div><div><div><div><div><div><div><div><div>"
                + "    <a href=\"http://item.rakuten.co.jp/seller4/item4/\">Item 6</a>"
                + "  </div></div></div></div></div></div></div></div></div></div>"
                + "</div>"
                + "</body></html>";

        java.nio.file.Files.write(tempFile.toPath(),
                htmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RakutenOrder order = rakutenOrderParser.parse(tempFile);

        assertNotNull(order);
        assertEquals("2023-05-09", order.getOrderDate().toString());
    }

    @Test
    public void testParse_FalseBranches() throws Exception {
        // This test specifically hits the false conditions of several if statements
        File tempFile = File.createTempFile("rakuten_false_branches", ".html");
        tempFile.deleteOnExit();

        String json = "{\"orderData\": {"
                + "\"orderStatusObject\": {"
                + "  \"statusHistory\": {" // not an array
                + "    \"some\": \"object\""
                + "  }"
                + "},"
                + "\"shippingList\": {" // not an array
                + "  \"some\": \"object\""
                + "}"
                + "}}";
        String htmlContent = "<html><body><div>注文番号 123456-20230509-000001</div><script>window.__INITIAL_STATE__ = "
                + json
                + ";</script></body></html>";
        java.nio.file.Files.write(tempFile.toPath(),
                htmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile);

        File tempFile2 = File.createTempFile("rakuten_false_branches_2", ".html");
        tempFile2.deleteOnExit();
        String json2 = "{\"orderData\": {"
                + "\"orderStatusObject\": {"
                + "  \"statusHistory\": ["
                + "    {\"name\": \"OPP_ORDER_STATUS_CANCELLED\", \"date\": \"short\"}" // name doesn't
                                                                                        // match, length
                                                                                        // <
                                                                                        // 10
                + "  ]"
                + "},"
                + "\"shippingList\": ["
                + "  {"
                + "    \"trackingNumber\": \"\"," // empty
                + "    \"deliveryInfo\": {"
                + "      \"deliveryCarrierName\": \"Yamato\","
                + "      \"deliveryDateTime\": \"\"" // empty
                + "    }"
                + "  }"
                + "]"
                + "}}";
        String htmlContent2 = "<html><body>"
                + "<script>window.__INITIAL_STATE__ = " + json2 + ";</script>"
                + "<div>注文番号 123456-20230509-000002</div>"
                + "<div>2023年11月10日 ご注文</div>" // Date pattern 2 with double digit day (length != 1)
                + "<div>数量 3 1000 円 <a href=\"http://item.rakuten.co.jp/seller/item_qty/\">Item Qty 3</a></div>" // Valid
                                                                                                                 // qty
                                                                                                                 // to
                                                                                                                 // hit
                                                                                                                 // qtyStr
                                                                                                                 // !=
                                                                                                                 // null
                + "</body></html>";
        java.nio.file.Files.write(tempFile2.toPath(),
                htmlContent2.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile2);

        // Test where orderStatusObject is missing
        File tempFile3 = File.createTempFile("rakuten_false_branches_3", ".html");
        tempFile3.deleteOnExit();
        String json3 = "{\"orderData\": {}}";
        String htmlContent3 = "<html><body><div>注文番号 123456-20230509-000003</div><script>window.__INITIAL_STATE__ = "
                + json3
                + ";</script></body></html>";
        java.nio.file.Files.write(tempFile3.toPath(),
                htmlContent3.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile3);

        // Test where orderData is missing
        File tempFile4 = File.createTempFile("rakuten_false_branches_4", ".html");
        tempFile4.deleteOnExit();
        String json4 = "{}";
        String htmlContent4 = "<html><body><div>注文番号 123456-20230509-000004</div><script>window.__INITIAL_STATE__ = "
                + json4
                + ";</script></body></html>";
        java.nio.file.Files.write(tempFile4.toPath(),
                htmlContent4.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile4);

        // Test container reaching null before depth 10
        File tempFile5 = File.createTempFile("rakuten_false_branches_5", ".html");
        tempFile5.deleteOnExit();
        String htmlContent5 = "<html><body><div>注文番号 123456-20230509-000005</div><div><a href=\"http://item.rakuten.co.jp/seller/item_root/\">Item</a></div></body></html>";
        java.nio.file.Files.write(tempFile5.toPath(),
                htmlContent5.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile5);

        // Test depth reaching 10 before container is null
        File tempFile6 = File.createTempFile("rakuten_false_branches_6", ".html");
        tempFile6.deleteOnExit();
        String html6 = "<html><body>"
                + "<div>注文番号 123456-20230509-000006</div>"
                + "<div id='d15'><div id='d14'><div id='d13'><div id='d12'><div id='d11'>"
                + "<div id='d10'><div id='d9'><div id='d8'><div id='d7'><div id='d6'>"
                + "<div id='d5'><div id='d4'><div id='d3'><div id='d2'><div id='d1'><div id='d0'>"
                + "<a href=\"http://item.rakuten.co.jp/seller/item_deep/\">Item Deep</a>"
                + "</div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div>"
                + "</body></html>";
        java.nio.file.Files.write(tempFile6.toPath(), html6.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile6);

        // Test partial match for 数量 and 円 (L201: contains("数量") but NOT contains("円"))
        File tempFile7 = File.createTempFile("rakuten_false_branches_7", ".html");
        tempFile7.deleteOnExit();
        String html7 = "<html><body><div>注文番号 123456-20230509-000007</div><div>数量 <a href=\"http://item.rakuten.co.jp/seller/item_qty_only/\">Item Qty Only</a></div></body></html>";
        java.nio.file.Files.write(tempFile7.toPath(), html7.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile7);

        // Test qtyStr == null branch (L214) when contains("数量") && contains("円") is
        // true
        File tempFile8 = File.createTempFile("rakuten_false_branches_8", ".html");
        tempFile8.deleteOnExit();
        String html8 = "<html><body><div>注文番号 123456-20230509-000008</div><div>数量 円 <a href=\"http://item.rakuten.co.jp/seller/item_qty_null/\">Item Qty Null</a></div></body></html>";
        java.nio.file.Files.write(tempFile8.toPath(), html8.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile8);

        // Test dateStr.length() < 10 in statusHistory (L247)
        File tempFile9 = File.createTempFile("rakuten_false_branches_9", ".html");
        tempFile9.deleteOnExit();
        String json9 = "{\"orderData\": {\"orderStatusObject\": {\"statusHistory\": [{\"name\": \"OPP_ORDER_STATUS_DELIVERED\", \"date\": \"short\"}]}}}";
        String html9 = "<html><body><div>注文番号 123456-20230509-000009</div><script>window.__INITIAL_STATE__ = " + json9
                + ";</script></body></html>";
        java.nio.file.Files.write(tempFile9.toPath(), html9.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile9);

        // Test dtMatch.find() == false in deliveryDateTime (L270)
        File tempFile10 = File.createTempFile("rakuten_false_branches_10", ".html");
        tempFile10.deleteOnExit();
        String json10 = "{\"orderData\": {\"shippingList\": [{\"trackingNumber\": \"123\", \"deliveryInfo\": {\"deliveryCarrierName\": \"Yamato\", \"deliveryDateTime\": \"Tomorrow\"}}]}}";
        String html10 = "<html><body><div>注文番号 123456-20230509-000010</div><script>window.__INITIAL_STATE__ = " + json10
                + ";</script></body></html>";
        java.nio.file.Files.write(tempFile10.toPath(),
                html10.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rakutenOrderParser.parse(tempFile10);
    }

    @Test
    public void testParse_NoDateFound() throws Exception {
        File tempFile = File.createTempFile("rakuten_no_date", ".html");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(),
                "<html><body><div>注文番号 123456-20230509-000001</div>No date here</body></html>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RakutenOrder order = rakutenOrderParser.parse(tempFile);
        assertNotNull(order);
        // Will fallback to LocalDate.now()
        assertEquals(java.time.LocalDate.now().toString(), order.getOrderDate().toString());
    }

}
