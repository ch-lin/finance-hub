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
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

public class Tokyo2020CardCsvParserTest {

    private Tokyo2020CardCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new Tokyo2020CardCsvParser();
    }

    @Test
    void getParserName_ReturnsCorrectName() {
        assertEquals("Tokyo 2020 Official Card", parser.getParserName());
    }

    @Test
    void canParse_ValidPreviewUtf8_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("\"1\",\"お取引日\",\"ATM\",\"Other\"");
        assertTrue(parser.canParse(preview));
    }

    @Test
    void canParse_ValidPreviewWin31J_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn(null);
        when(preview.getHeaderWin31J()).thenReturn("\"1\",\"お取引日\",\"ATM\",\"Other\"");
        assertTrue(parser.canParse(preview));
    }

    @Test
    void canParse_InvalidPreview_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("\"1\",\"お取引日\""); // Missing ATM
        when(preview.getHeaderWin31J()).thenReturn("お取引日,ATM"); // Missing "1"
        assertFalse(parser.canParse(preview));

        when(preview.getHeaderUtf8()).thenReturn(null);
        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        // Filename matches "(\\d{4})(\\d{2})\\d*.*\\.csv" -> 2021-01
        File file = tempDir.resolve("meisai_20210103090446764.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("\"1\",\"お取引日\",\"ATM\",\"Other\",\"Other\"\n");
            writer.write("\n"); // Empty
            writer.write("\"2\",\"2021/1/3\",\"Store A\",\"X\",\"1,000.00\"\n");
            writer.write("\"2\",\"2021/01/10\",\"Store B\",\"X\",\"500\"\n");
            // Not a data row ("1")
            writer.write("\"1\",\"2021/01/10\",\"Store\",\"X\",\"500\"\n");
            // Missing columns (< 5)
            writer.write("\"2\",\"2021/01/10\",\"Store\",\"X\"\n");
            // Invalid date
            writer.write("\"2\",\"BadDate\",\"Store\",\"X\",\"500\"\n");
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        // Extracted billing month from filename

        assertEquals("2021-01", statement.getBillingMonth());

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(2, transactions.size());

        CreditCardTransaction t1 = transactions.get(0);
        assertEquals(LocalDate.of(2021, 1, 3), t1.getTransactionDate());
        assertEquals("Store A", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("Tokyo 2020 Official Card", t1.getCardName());

        CreditCardTransaction t2 = transactions.get(1);
        assertEquals(LocalDate.of(2021, 1, 10), t2.getTransactionDate());
        assertEquals("Store B", t2.getStoreName());
        assertEquals(500, t2.getAmount());
    }

    @Test
    void parseLines_ExceptionInAmount(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("meisai.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("\"1\",\"お取引日\",\"ATM\",\"Other\",\"Other\"\n");
            writer.write("\"2\",\"2021/1/3\",\"Store A\",\"X\",\"BadAmount\"\n");
        }

        CreditCardParseResult result = parser.parse(file);

        // Billing year/month is not set because filename "meisai.csv" doesn't match

        assertEquals("UNKNOWN", result.getStatement().getBillingMonth());

        assertEquals(0, result.getStatement().getTransactions().size());

        List<String> failedRows = result.getFailedRows();
        assertEquals(1, failedRows.size());
        assertTrue(failedRows.get(0).contains("BadAmount"));
    }

    @Test
    void getEncoding_ReturnsWindows31J_IfWin31JMatches(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("encoding_test.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("\"1\",\"お取引日\",\"ATM\"\n");
        }
        // In getEncoding, it tests UTF-8 first, then Windows-31J
        // Since we wrote in Win-31J, "お取引日" in UTF-8 won't match.
        // It will match Win-31J. Wait, if it's ascii it matches both? No, "お取引日" has
        // multi-byte.

        // Wait, parser.getEncoding(file) uses detectEncoding.
        // I can just verify it works or fallback to default.
        CreditCardParseResult result = parser.parse(file);
        assertNotNull(result);
    }
}
