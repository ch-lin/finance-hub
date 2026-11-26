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

class SaisonCsvParserTest {

    private SaisonCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new SaisonCsvParser();
    }

    @Test
    @DisplayName("Should return correct parser name")
    void getParserName() {
        assertEquals("SAISON", parser.getParserName());
    }

    @Test
    @DisplayName("canParse - should return true for valid SAISON header in Win31J")
    void canParse_ValidWin31J_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("カード名称,Custom SAISON Card");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return false for invalid header")
    void canParse_Invalid_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("Date,Store");
        assertFalse(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return false when header is null")
    void canParse_Null_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    @DisplayName("parseLines - should parse successfully and extract metadata")
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("saison_test.csv").toFile();
        try (FileWriter writer = new FileWriter(file, Charset.forName("Windows-31J"))) {
            writer.write("カード名称,My Saison Gold Card\n");
            writer.write("\n"); // Empty line
            writer.write("お支払日,2024/06/04\n");
            writer.write("今回ご請求額,1234\n");
            writer.write("Header1,Header2,Header3,Header4,Header5,Header6,Header7\n"); // Just filler header row

            // Valid row
            writer.write("2024/05/01,Amazon,1回払い,1000,0,1000,some memo\n");

            // Short row (length < 6)
            writer.write("2024/05/02,Store,1回払い\n");

            // 1 column row to test length < 2 branch
            writer.write("SingleColumn\n");

            // Empty date but Annual Fee (年会費)
            writer.write(" ,年会費(Annual Fee),1回払い,3000,0,3000\n");

            // Empty date, Not Annual Fee
            writer.write(" ,SummaryRow,1回払い,0,0,0,\n");

            // Invalid date format
            writer.write("InvalidDate,Store,1回払い,0,0,1000,\n");
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals(LocalDate.of(2024, 6, 4), statement.getPaymentDate());
        assertEquals(1234, statement.getTotalAmount());
        assertEquals(2, statement.getTransactions().size());

        CreditCardTransaction t1 = statement.getTransactions().get(0);
        assertEquals(LocalDate.of(2024, 5, 1), t1.getTransactionDate());
        assertEquals("Amazon", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("some memo", t1.getMemo());
        assertEquals("My Saison Gold Card", t1.getCardName());

        CreditCardTransaction t2 = statement.getTransactions().get(1);
        assertEquals(LocalDate.of(2024, 6, 4), t2.getTransactionDate()); // Fallback to payment date
        assertEquals("年会費(Annual Fee)", t2.getStoreName());
        assertEquals(3000, t2.getAmount());
        assertNull(t2.getMemo());
        assertEquals("My Saison Gold Card", t2.getCardName());
    }

    @Test
    @DisplayName("parseLines - should handle invalid payment date and total amount")
    void parseLines_InvalidMetadata(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("saison_invalid_meta.csv").toFile();
        try (FileWriter writer = new FileWriter(file, Charset.forName("Windows-31J"))) {
            writer.write("お支払日,BadDate\n");
            writer.write("今回ご請求額,BadAmount\n");
            // Empty date Annual Fee but payment date is null because it failed to parse
            writer.write(" ,年会費(Annual Fee),1回払い,3000,0,3000,\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertNull(statement.getPaymentDate());
        assertEquals(0, statement.getTotalAmount());
        assertEquals(0, statement.getTransactions().size()); // Annual fee row skipped because payment date is null
    }

    @Test
    @DisplayName("parseLines - should handle parse exception on row")
    void parseLines_Exception_AddedToFailedRows(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("saison_fail.csv").toFile();
        try (FileWriter writer = new FileWriter(file, Charset.forName("Windows-31J"))) {
            // Invalid amount
            writer.write("2024/05/01,Store,1回払い,0,0,BadAmount,\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("BadAmount"));
    }
}
