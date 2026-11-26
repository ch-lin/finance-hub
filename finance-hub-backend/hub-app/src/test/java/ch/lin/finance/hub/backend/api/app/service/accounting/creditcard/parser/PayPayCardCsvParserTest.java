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
package ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.parser;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

class PayPayCardCsvParserTest {

    private PayPayCardCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new PayPayCardCsvParser();
    }

    @Test
    @DisplayName("Should return correct parser name")
    void getParserName() {
        assertEquals("PayPay Card", parser.getParserName());
    }

    @Test
    @DisplayName("canParse - should return true for valid PayPay header in UTF-8")
    void canParse_ValidUtf8_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("利用日/キャンセル日,利用店名・商品名,利用者,支払区分,利用金額,手数料,当月お支払日");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return true for valid PayPay header in Win31J")
    void canParse_ValidWin31J_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn(null);
        when(preview.getHeaderWin31J()).thenReturn("利用日/キャンセル日,利用店名・商品名");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return false for invalid header")
    void canParse_Invalid_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("Date,Store,Amount");
        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));

        FilePreview missingStore = mock(FilePreview.class);
        when(missingStore.getHeaderUtf8()).thenReturn("利用日/キャンセル日,利用者,支払区分,利用金額"); // missing store
        when(missingStore.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(missingStore));
    }

    @Test
    @DisplayName("canParse - should return false when headers are null")
    void canParse_Null_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn(null);
        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    @DisplayName("getEncoding - should default to UTF-8")
    void getEncoding_DetectsCorrectly(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("paypay_test.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("利用日/キャンセル日,利用店名・商品名\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(StandardCharsets.UTF_8, result.getCharset());
    }

    @Test
    @DisplayName("parseLines - should parse successfully with dynamic indices")
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("paypay_data.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            // Write header (indices: Date=0, Store=1, User=2, Memo=3, Amount=4, Fee=5,
            // PaymentDate=6)
            writer.write("利用日/キャンセル日,利用店名・商品名,利用者,支払区分,利用金額,手数料,当月お支払日\n");
            writer.write("\n"); // Empty line

            // Valid row
            writer.write("2024/6/1,\"Amazon.co.jp\",本人,1回払い,1000,0,2024/7/27\n");

            // Missing payment date
            writer.write("2024/6/2,Store B,本人,リボ払い,500,,\n");

            // Empty date
            writer.write(",Store C,本人,,100,,\n");

            // Invalid date format
            writer.write("InvalidDate,Store D,本人,1回払い,200,0,\n");

            // Short row
            writer.write("2024/6/3,Store E\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals(2, statement.getTransactions().size());
        assertEquals(LocalDate.of(2024, 7, 27), statement.getPaymentDate());

        CreditCardTransaction t1 = statement.getTransactions().get(0);
        assertEquals(LocalDate.of(2024, 6, 1), t1.getTransactionDate());
        assertEquals("Amazon.co.jp", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("1回払い", t1.getMemo());
        assertEquals("PayPay Card", t1.getCardName());

        CreditCardTransaction t2 = statement.getTransactions().get(1);
        assertEquals(LocalDate.of(2024, 6, 2), t2.getTransactionDate());
        assertEquals("Store B", t2.getStoreName());
        assertEquals(500, t2.getAmount());
        assertEquals("リボ払い", t2.getMemo());
    }

    @Test
    @DisplayName("parseLines - should handle invalid transaction correctly")
    void parseLines_Exception_AddedToFailedRows(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("paypay_fail.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("利用日/キャンセル日,利用店名・商品名,利用者,支払区分,利用金額\n");
            // Invalid amount
            writer.write("2024/6/1,Store,本人,1回払い,BadAmount\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("BadAmount"));
    }

    @Test
    @DisplayName("parseLines - should handle invalid payment date cleanly")
    void parseLines_InvalidPaymentDate(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("paypay_invalid_payment_date.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("利用日/キャンセル日,利用店名・商品名,利用者,支払区分,利用金額,当月お支払日\n");
            // Valid row but payment date is invalid
            writer.write("2024/6/1,Store,本人,1回払い,1000,BadDate\n");
            // Valid row but too short to contain payment date (cols.length <=
            // paymentDateIndex)
            writer.write("2024/6/2,Store,本人,1回払い,2000\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(0, result.getFailedRows().size());
        assertEquals(2, result.getStatement().getTransactions().size());
        assertNull(result.getStatement().getPaymentDate());
    }

    @Test
    @DisplayName("parseLines - should handle missing payment date on first row")
    void parseLines_MissingPaymentDateFirstRow(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("paypay_missing_payment_first.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("利用日/キャンセル日,利用店名・商品名,利用者,支払区分,利用金額,当月お支払日\n");
            // First valid row has empty payment date
            writer.write("2024/6/1,Store,本人,1回払い,1000,\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(0, result.getFailedRows().size());
        assertEquals(1, result.getStatement().getTransactions().size());
        assertNull(result.getStatement().getPaymentDate());
    }

    @Test
    @DisplayName("parseLines - should use fallback indices if header doesn't define columns")
    void parseLines_FallbackIndices(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("paypay_fallback.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            // Header doesn't have named columns "利用金額", "支払区分", etc.
            writer.write("Date,Store,User,Memo,Amount\n");
            // Row
            writer.write("2024/6/1,Store,User,FallbackMemo,2000\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(1, result.getStatement().getTransactions().size());
        CreditCardTransaction t = result.getStatement().getTransactions().get(0);
        assertEquals(2000, t.getAmount());
        assertEquals("FallbackMemo", t.getMemo());
    }

    @Test
    @DisplayName("parseLines - should handle missing memo cleanly")
    void parseLines_MissingMemoSafe(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("paypay_nomemo.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            // Set amount before memo so we can have a row that includes amount but misses
            // memo
            writer.write("利用日/キャンセル日,利用店名・商品名,利用金額,支払区分\n");
            // Date=0, Store=1, Amount=2, Memo=3
            writer.write("2024/6/1,Store,1000\n"); // length=3
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(1, result.getStatement().getTransactions().size());
        CreditCardTransaction t = result.getStatement().getTransactions().get(0);
        assertEquals(1000, t.getAmount());
        assertNull(t.getMemo());
    }
}
