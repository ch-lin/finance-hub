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

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;

class AeonCsvParserTest {

    private AeonCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new AeonCsvParser();
    }

    @Test
    @DisplayName("Should identify AEON file correctly")
    void canParse_ValidAeon_ReturnsTrue() {
        FilePreview preview = org.mockito.Mockito.mock(FilePreview.class);
        org.mockito.Mockito.when(preview.getHeaderWin31J()).thenReturn("ご利用カード,AEON CARD");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("Should reject non-AEON file")
    void canParse_InvalidAeon_ReturnsFalse() {
        FilePreview preview = org.mockito.Mockito.mock(FilePreview.class);
        org.mockito.Mockito.when(preview.getHeaderWin31J()).thenReturn("種別（ショッピング,");
        assertFalse(parser.canParse(preview));

        FilePreview nullPreview = org.mockito.Mockito.mock(FilePreview.class);
        org.mockito.Mockito.when(nullPreview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(nullPreview));
    }

    @Test
    @DisplayName("Should parse standard AEON CSV format successfully")
    void parse_ValidCsv_ReturnsResult(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("aeon_statement.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("ご利用カード,Test AEON Card\n");
            writer.write("ご請求金額, 10000\n");
            writer.write("お支払日, 2023年 8月 2日\n");
            writer.write("\n");
            writer.write("230715,,Supermarket,,,,1500,Groceries\n");
            writer.write(",,Discount,,,,-200,\n"); // Inherits date 230715
            writer.write("230720,,Restaurant,,,,8700,\n");
            writer.write("SomeOtherHeader, 1234\n"); // Unknown metadata header
            writer.write("JustOneColumn\n"); // cols.length < 2
            writer.write("1,2,3,4,5\n"); // cols.length >= 2, but data row cols.length < 7
            writer.write("230722,, ,,,,1000,\n"); // storeName empty
            writer.write("230723,,Store,,,, ,\n"); // amountStr empty
            writer.write("230724,,Store7Cols,,,,1500\n"); // cols.length == 7 (no memo column)
            writer.write("230721,,InvalidAmount,,,,ABC,\n"); // Invalid amount, should be ignored and warned
            writer.write("InvalidDate,,Store,,,,1000,\n"); // Invalid date, should be ignored
        }

        CreditCardParseResult result = parser.parse(csvFile);

        assertNotNull(result);
        assertTrue(result.getFailedRows().isEmpty());

        CreditCardStatement statement = result.getStatement();
        assertNotNull(statement);
        assertEquals("Test AEON Card", statement.getCardName());
        assertEquals(10000, statement.getTotalAmount());
        assertEquals("2023-08", statement.getBillingMonth()); // from payment date
        assertEquals(LocalDate.of(2023, 8, 2), statement.getPaymentDate());

        assertEquals(4, statement.getTransactions().size());

        assertEquals(LocalDate.of(2023, 7, 15), statement.getTransactions().get(0).getTransactionDate());
        assertEquals("Supermarket", statement.getTransactions().get(0).getStoreName());
        assertEquals(1500, statement.getTransactions().get(0).getAmount());
        assertEquals("Groceries", statement.getTransactions().get(0).getMemo());
        assertEquals("Test AEON Card", statement.getTransactions().get(0).getCardName());

        assertEquals(LocalDate.of(2023, 7, 15), statement.getTransactions().get(1).getTransactionDate());
        assertEquals("Discount", statement.getTransactions().get(1).getStoreName());
        assertEquals(-200, statement.getTransactions().get(1).getAmount());
        assertNull(statement.getTransactions().get(1).getMemo());

        assertEquals(LocalDate.of(2023, 7, 20), statement.getTransactions().get(2).getTransactionDate());
        assertEquals("Restaurant", statement.getTransactions().get(2).getStoreName());
        assertEquals(8700, statement.getTransactions().get(2).getAmount());

        assertEquals(LocalDate.of(2023, 7, 24), statement.getTransactions().get(3).getTransactionDate());
        assertEquals("Store7Cols", statement.getTransactions().get(3).getStoreName());
        assertEquals(1500, statement.getTransactions().get(3).getAmount());
        assertNull(statement.getTransactions().get(3).getMemo());
    }

    @Test
    @DisplayName("Should handle missing dates with no prior date")
    void parse_MissingDateNoPrior_Ignored(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("aeon_nodate.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write(",,MissingDateStore,,,,, 1000 ,\n");
        }
        CreditCardParseResult result = parser.parse(csvFile);
        assertTrue(result.getStatement().getTransactions().isEmpty());
    }

    @Test
    @DisplayName("Should handle bad metadata safely")
    void parse_BadMetadata_SafelyHandled(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("aeon_bad_meta.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("ご請求金額, XXXX \n");
            writer.write("お支払日, 2023年 2月 30日 \n"); // Invalid date (February 30th) throws DateTimeException
            writer.write("お支払日, InvalidDate \n"); // Does not match Regex
        }
        CreditCardParseResult result = parser.parse(csvFile);
        assertNull(result.getStatement().getPaymentDate());
        assertEquals(0, result.getStatement().getTotalAmount());
    }

    @Test
    @DisplayName("Should handle parsing error and add to failedRows")
    void parse_Exception_AddsToFailedRows(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("aeon_fail.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            // valid date, valid amount format but number out of bounds for Integer
            writer.write("230715,,Huge,,,,999999999999999999,\n");
        }
        CreditCardParseResult result = parser.parse(csvFile);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("999999999999999999"));
    }
}
