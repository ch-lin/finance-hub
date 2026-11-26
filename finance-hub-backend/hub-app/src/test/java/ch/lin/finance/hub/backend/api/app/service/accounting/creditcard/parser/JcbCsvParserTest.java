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
import java.nio.charset.Charset;
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

class JcbCsvParserTest {

    private JcbCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new JcbCsvParser();
    }

    @Test
    @DisplayName("getParserName - should return 'JCB'")
    void getParserName() {
        assertEquals("JCB", parser.getParserName());
    }

    @Test
    @DisplayName("canParse - should return true for valid JCB header")
    void canParse_ValidHeader() {
        FilePreview preview1 = mock(FilePreview.class);
        when(preview1.getHeaderWin31J()).thenReturn("\"\",\"\",\"今回のお振替日\",\"2024/08/10\"");
        assertTrue(parser.canParse(preview1));

        FilePreview preview2 = mock(FilePreview.class);
        when(preview2.getHeaderWin31J()).thenReturn("\"\",\"\",\"今回のお支払日\",\"2024/08/10\"");
        assertTrue(parser.canParse(preview2));
    }

    @Test
    @DisplayName("canParse - should return false for invalid header")
    void canParse_InvalidHeader() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("ご利用年月日,利用店名等,お支払方法");
        assertFalse(parser.canParse(preview));

        FilePreview nullPreview = mock(FilePreview.class);
        when(nullPreview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(nullPreview));
    }

    @Test
    @DisplayName("parseLines - should parse successfully")
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("JCB_202408.csv").toFile();
        try (FileWriter writer = new FileWriter(file, Charset.forName("Windows-31J"))) {
            // Write Payment Date
            writer.write("\"\",\"\",\"今回のお振替日\",\"2024/08/10\"\n");
            // Write Total Amount
            writer.write("\"\",\"\",\"合計(円)\",\"15,500\"\n");
            // Write Header
            writer.write("\"ご利用者\",\"\",\"ご利用日\",\"ご利用店名等\",\"ご利用金額(円)\",\"\",\"\",\"\",\"\",\"\",\"摘要\"\n");

            // Transaction 1: JCB formatted card
            writer.write(
                    "\"****-****-****-1019 【ＪＣＢ】カードＷ ＮＬ　林　哲宏　様\",\"\",\"2024/07/01\",\"Amazon\",\"1,000\",\"\",\"\",\"\",\"\",\"\",\"amazon.co.jp\"\n");
            // Transaction 2: Non-JCB formatted card (>= 4 parts)
            writer.write(
                    "\"1111-2222-3333-4444　セブンカード　林　哲宏　様\",\"\",\"2024/07/05\",\"StoreA\",\"2000\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
            // Transaction 3: JCB short name
            writer.write(
                    "\"****-****-****-1019 【ＪＣＢ】カード\",\"\",\"2024/07/10\",\"StoreB\",\"3000\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
            // Transaction 4: Non-JCB short name (< 4 parts)
            writer.write(
                    "\"1111-2222-3333-4444　カード\",\"\",\"2024/07/15\",\"StoreC\",\"4000\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
            // Transaction 5: Empty line to be skipped
            writer.write("\n");
            // Transaction 6: Ignored row (no date)
            writer.write("\"Summary\",\"\",\"\",\"\",\"5000\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
            // Transaction 7: JCB formatted card with spaces (parts.length >= 5) to hit
            // ternary true branch
            writer.write(
                    "\"****-****-****-1019 【ＪＣＢ】カードＷ　ＮＬ　林　哲宏　様\",\"\",\"2024/07/20\",\"StoreD\",\"5000\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
            // Transaction 8: Non-JCB formatted card with spaces (parts.length >= 6) to hit
            // ternary true branch
            writer.write(
                    "\"1111-2222-3333-4444　セブン　カード　林　哲宏　様\",\"\",\"2024/07/25\",\"StoreE\",\"6000\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
            // Transaction 9: Short row length (cols.length < 5)
            writer.write("\"ShortRow\"\n");
            // Transaction 10: Short row length (cols.length < 10 for memo ternary
            // true/false branch)
            writer.write("\"1111-2222-3333-4444　カード\",\"\",\"2024/07/30\",\"StoreF\",\"7000\"\n");
            // Transaction 11: Non-JCB formatted card where extracted name is empty
            // (parts.length >= 4, extracted empty)
            writer.write(
                    "\"1111-2222-3333-4444　　　様\",\"\",\"2024/08/01\",\"StoreG\",\"8000\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals(LocalDate.of(2024, 8, 10), statement.getPaymentDate());
        assertEquals(15500, statement.getTotalAmount());
        // Since getBillingMonth requires "tryExtractBillingMonthFromFilename" which
        // expects yyyyMM regex...
        // For JCB, AbstractCsvParser doesn't extract it by default. So it should be
        // UNKNOWN.
        assertEquals("2024-08", statement.getBillingMonth());

        assertEquals(8, statement.getTransactions().size());

        CreditCardTransaction t1 = statement.getTransactions().get(0);
        assertEquals("JCB カードＷ ＮＬ", t1.getCardName());
        assertEquals(LocalDate.of(2024, 7, 1), t1.getTransactionDate());
        assertEquals("Amazon", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("amazon.co.jp", t1.getMemo());

        CreditCardTransaction t2 = statement.getTransactions().get(1);
        assertEquals("セブンカード", t2.getCardName());
        assertEquals(2000, t2.getAmount());

        CreditCardTransaction t3 = statement.getTransactions().get(2);
        assertEquals("JCB カード", t3.getCardName()); // JCB short name
        assertEquals(3000, t3.getAmount());

        CreditCardTransaction t4 = statement.getTransactions().get(3);
        assertEquals("JCB", t4.getCardName()); // Default JCB name because non-jcb logic extracted empty or skipped
        assertEquals(4000, t4.getAmount());

        CreditCardTransaction t5 = statement.getTransactions().get(4);
        assertEquals("JCB カードＷ ＮＬ", t5.getCardName()); // Tests JCB ternary
        assertEquals(5000, t5.getAmount());

        CreditCardTransaction t6 = statement.getTransactions().get(5);
        assertEquals("セブン カード", t6.getCardName()); // Tests Non-JCB ternary
        assertEquals(6000, t6.getAmount());

        CreditCardTransaction t7 = statement.getTransactions().get(6);
        assertEquals("JCB", t7.getCardName()); // Tests cols.length < 10 memo branch
        assertEquals(7000, t7.getAmount());
        assertNull(t7.getMemo());

        CreditCardTransaction t8 = statement.getTransactions().get(7);
        assertEquals("JCB", t8.getCardName()); // Default name because extracted is empty
        assertEquals(8000, t8.getAmount());
    }

    @Test
    @DisplayName("parseLines - should handle invalid metadata and transaction errors")
    void parseLines_InvalidMetadata(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("JCB_Error.csv").toFile();
        try (FileWriter writer = new FileWriter(file, Charset.forName("Windows-31J"))) {
            writer.write("\"\",\"\",\"今回のお支払日\",\"BadDate\"\n");
            writer.write("\"\",\"\",\"合計(円)\",\"BadAmount\"\n");
            writer.write("\"ご利用者\"\n");
            writer.write("\"\",\"\",\"BadDate\",\"Store\",\"1000\",\"\",\"\",\"\",\"\",\"\",\"\"\n"); // Invalid
                                                                                                      // transaction
                                                                                                      // date
            writer.write("\"\",\"\",\"2024/07/01\",\"Store\",\"BadAmount\",\"\",\"\",\"\",\"\",\"\",\"\"\n"); // Invalid
                                                                                                              // amount
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("BadAmount"));

        CreditCardStatement statement = result.getStatement();
        assertNull(statement.getPaymentDate());
        assertEquals(0, statement.getTotalAmount()); // Exception caught, defaulted to 0 initially
        assertEquals(0, statement.getTransactions().size());
    }
}
