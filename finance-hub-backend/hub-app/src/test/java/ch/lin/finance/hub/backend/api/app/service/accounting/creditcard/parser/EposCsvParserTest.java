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

class EposCsvParserTest {

    private EposCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new EposCsvParser();
    }

    @Test
    @DisplayName("Should identify EPOS file correctly")
    void canParse_ValidEpos_ReturnsTrue() {
        FilePreview preview = org.mockito.Mockito.mock(FilePreview.class);
        org.mockito.Mockito.when(preview.getHeaderWin31J()).thenReturn("種別（ショッピング）,Other Info");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("Should reject non-EPOS file")
    void canParse_InvalidEpos_ReturnsFalse() {
        FilePreview preview = org.mockito.Mockito.mock(FilePreview.class);
        org.mockito.Mockito.when(preview.getHeaderWin31J()).thenReturn("ご利用カード,AEON");
        assertFalse(parser.canParse(preview));

        FilePreview nullPreview = org.mockito.Mockito.mock(FilePreview.class);
        org.mockito.Mockito.when(nullPreview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(nullPreview));
    }

    @Test
    @DisplayName("Should parse standard EPOS CSV format successfully")
    void parse_ValidCsv_ReturnsResult(@TempDir Path tempDir) throws Exception {
        // Includes billing month in filename
        File csvFile = tempDir.resolve("20230812_UseDetailReference.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("種別（ショッピング）、ご利用年月日、ご利用店舗、ご利用内容、ご利用金額\n");
            writer.write("1回払い,2023年07月10日,Store A,, 1500 \n");
            writer.write("1回払い,2023年07月20日,Store B,MemoB, 3000 \n");
            writer.write("1回払い,2023年07月25日,Store C,MemoC, 2000 ,Extra\n"); // 6 columns to test branch
            writer.write("1回払い,ご利用年月日,Header,ShouldBeSkipped, 0 \n"); // Should be skipped
            writer.write("1回払い,,MissingDateStore,MissingDateMemo, 5000 \n"); // Should be skipped
            writer.write("お支払合計,,,,, 4500 ,\n"); // Summary row
        }

        CreditCardParseResult result = parser.parse(csvFile);

        assertNotNull(result);
        assertTrue(result.getFailedRows().isEmpty());

        CreditCardStatement statement = result.getStatement();
        assertNotNull(statement);
        assertEquals("EPOS Card", statement.getCardName());
        assertEquals(4500, statement.getTotalAmount());
        assertEquals("2023-08", statement.getBillingMonth());

        assertEquals(3, statement.getTransactions().size());

        assertEquals(LocalDate.of(2023, 7, 10), statement.getTransactions().get(0).getTransactionDate());
        assertEquals("Store A", statement.getTransactions().get(0).getStoreName());
        assertEquals(1500, statement.getTransactions().get(0).getAmount());
        assertNull(statement.getTransactions().get(0).getMemo());
        assertEquals("EPOS Card", statement.getTransactions().get(0).getCardName());

        assertEquals(LocalDate.of(2023, 7, 20), statement.getTransactions().get(1).getTransactionDate());
        assertEquals("Store B", statement.getTransactions().get(1).getStoreName());
        assertEquals(3000, statement.getTransactions().get(1).getAmount());
        assertEquals("MemoB", statement.getTransactions().get(1).getMemo());

        assertEquals(LocalDate.of(2023, 7, 25), statement.getTransactions().get(2).getTransactionDate());
        assertEquals("Store C", statement.getTransactions().get(2).getStoreName());
        assertEquals(2000, statement.getTransactions().get(2).getAmount());
        assertEquals("MemoC", statement.getTransactions().get(2).getMemo());
    }

    @Test
    @DisplayName("Should handle parsing error safely")
    void parse_Exception_AddsToFailedRows(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("epos_fail.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("Header\n");
            writer.write("1回払い,2023年07月10日,Store A,, NotANumber \n");
        }
        CreditCardParseResult result = parser.parse(csvFile);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("NotANumber"));
    }

    @Test
    @DisplayName("Should handle malformed footer safely")
    void parse_BadFooter_SafelyHandled(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("epos_bad_footer.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("Header\n");
            writer.write("お支払合計,,,,, BadTotal ,\n");
        }
        CreditCardParseResult result = parser.parse(csvFile);
        assertEquals(0, result.getStatement().getTotalAmount());
    }

    @Test
    @DisplayName("Should reject invalid date rows")
    void parse_InvalidDateRow_Ignored(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("epos_invalid_date.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("Header\n");
            writer.write("1回払い,2023/07/10,Store A,, 1500 \n"); // Wrong date format
        }
        CreditCardParseResult result = parser.parse(csvFile);
        assertTrue(result.getStatement().getTransactions().isEmpty());
    }

    @Test
    @DisplayName("Should skip rows with too few columns")
    void parse_ShortRow_Ignored(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("epos_short.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("Header\n");
            writer.write("\n"); // Empty line
            writer.write("1回払い,2023年07月10日,Store\n"); // Only 3 columns
            writer.write("お支払合計,1000\n"); // Short summary row
        }
        CreditCardParseResult result = parser.parse(csvFile);
        assertTrue(result.getStatement().getTransactions().isEmpty());
    }
}
