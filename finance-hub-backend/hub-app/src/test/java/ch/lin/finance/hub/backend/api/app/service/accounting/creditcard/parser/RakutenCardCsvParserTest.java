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

class RakutenCardCsvParserTest {

    private RakutenCardCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new RakutenCardCsvParser();
    }

    @Test
    @DisplayName("getParserName - should return 'Rakuten Card'")
    void getParserName() {
        assertEquals("Rakuten Card", parser.getParserName());
    }

    @Test
    @DisplayName("canParse - should return true for valid Rakuten header")
    void canParse_ValidHeader() {
        FilePreview preview1 = mock(FilePreview.class);
        when(preview1.getHeaderUtf8()).thenReturn("利用日,利用店名・商品名,利用者,支払方法,利用金額,支払手数料,支払総額");
        assertTrue(parser.canParse(preview1));

        FilePreview preview2 = mock(FilePreview.class);
        when(preview2.getHeaderUtf8()).thenReturn(null);
        when(preview2.getHeaderWin31J()).thenReturn("利用日,利用店名・商品名,利用者,支払方法,利用金額,支払手数料,支払総額");
        assertTrue(parser.canParse(preview2));
    }

    @Test
    @DisplayName("canParse - should return false for invalid header")
    void canParse_InvalidHeader() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("利用日,利用店名・商品名,支払区分,利用金額"); // Yahoo uses 支払区分
        when(preview.getHeaderWin31J()).thenReturn("利用日,利用店名・商品名,支払区分,利用金額");
        assertFalse(parser.canParse(preview));

        FilePreview previewMissingAmount = mock(FilePreview.class);
        when(previewMissingAmount.getHeaderUtf8()).thenReturn("利用日,利用店名・商品名,支払方法");
        when(previewMissingAmount.getHeaderWin31J()).thenReturn("利用日,利用店名・商品名,支払方法");
        assertFalse(parser.canParse(previewMissingAmount));

        FilePreview nullPreview = mock(FilePreview.class);
        when(nullPreview.getHeaderUtf8()).thenReturn(null);
        when(nullPreview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(nullPreview));
    }

    @Test
    @DisplayName("getEncoding - should detect encoding correctly")
    void getEncoding(@TempDir Path tempDir) throws Exception {
        File fileUtf8 = tempDir.resolve("enavi202408.csv").toFile();
        try (FileWriter writer = new FileWriter(fileUtf8, StandardCharsets.UTF_8)) {
            writer.write("利用日,利用店名・商品名,利用者,支払方法,利用金額\n");
        }
        assertEquals("UTF-8", parser.getEncoding(fileUtf8));

        File fileWin31J = tempDir.resolve("enavi202409.csv").toFile();
        try (FileWriter writer = new FileWriter(fileWin31J, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("利用日,利用店名・商品名,利用者,支払方法,利用金額\n");
        }
        assertEquals("Windows-31J", parser.getEncoding(fileWin31J));
    }

    @Test
    @DisplayName("parseLines - should parse successfully")
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("enavi202408.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("利用日,利用店名・商品名,利用者,支払方法,利用金額\n"); // Header
            writer.write("\n"); // Empty
            writer.write("2024/07/01,Amazon,家族,1回払い,1000\n"); // Transaction with memo and no comma
            writer.write("2024/07/05,Store,本人, ,2000\n"); // Transaction with empty memo (space)
            writer.write("2024/07/10,NoAmount,本人,1回払い,\n"); // Empty amount
            writer.write("Short,Row,Here\n"); // Short row
            writer.write(",NoDate,1回払い,1000\n"); // No date (cols.length < 5)
            writer.write(",NoDate,1回払い,1000,5000\n"); // No date (cols.length >= 5, cols[0].isEmpty())
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals("2024-08", statement.getBillingMonth());
        assertEquals(3000, statement.getTotalAmount()); // 1000 + 2000 + 0

        assertEquals(3, statement.getTransactions().size());

        CreditCardTransaction t1 = statement.getTransactions().get(0);
        assertEquals("Rakuten Card", t1.getCardName());
        assertEquals(LocalDate.of(2024, 7, 1), t1.getTransactionDate());
        assertEquals("Amazon", t1.getStoreName());
        assertEquals("1回払い", t1.getMemo());
        assertEquals(1000, t1.getAmount());

        CreditCardTransaction t2 = statement.getTransactions().get(1);
        assertEquals(LocalDate.of(2024, 7, 5), t2.getTransactionDate());
        assertEquals(2000, t2.getAmount());
        assertEquals(null, t2.getMemo()); // Blank memo

        CreditCardTransaction t3 = statement.getTransactions().get(2);
        assertEquals(0, t3.getAmount()); // Empty amount becomes 0
    }

    @Test
    @DisplayName("parseLines - should handle invalid transaction errors")
    void parseLines_InvalidMetadata(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("enavi_Bad.csv").toFile(); // no billing month in name
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("利用日,利用店名・商品名,利用者,支払方法,利用金額\n"); // Header
            writer.write("BadDate,Store,本人,1回払い,1000\n"); // Invalid date -> skipped
            writer.write("2024/07/01,Store,本人,1回払い,BadAmount\n"); // Invalid amount -> failedRow
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("BadAmount"));

        CreditCardStatement statement = result.getStatement();
        assertEquals("UNKNOWN", statement.getBillingMonth()); // No filename match, 0 valid tx
        assertEquals(0, statement.getTransactions().size());
    }
}
