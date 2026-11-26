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

public class GoldPointCardCsvParserTest {

    private GoldPointCardCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new GoldPointCardCsvParser();
    }

    @Test
    void getParserName_ReturnsCorrectName() {
        assertEquals("Gold Point Card", parser.getParserName());
    }

    @Test
    void canParse_ValidPreview_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("1111-****-****-1111,氏名,ゴールドポイントカードプラス");
        assertTrue(parser.canParse(preview));
    }

    @Test
    void canParse_InvalidPreview_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("Other Card Header");
        assertFalse(parser.canParse(preview));

        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("202407.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("1111,name,ゴールドポイントカードプラス\n"); // First line with card name
            writer.write("\n"); // Empty
            writer.write("2024/07/01,Amazon,1000\n"); // Format 1 (amount in col 2)
            writer.write("2024/07/05,Store,x,x,x,x,2000\n"); // Format 2 (amount in col 6, length >= 7)
            writer.write("Short,Row\n"); // Short row < 3 cols
            writer.write("SingleColumn\n"); // Even shorter row < 2 cols
            writer.write("BadDate,Store,1000\n"); // Invalid date
            writer.write(" , , , , ,136,\n"); // Total row (cols.length >= 6, cols[1] empty, cols[5] = 136)
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(2, transactions.size());

        CreditCardTransaction t1 = transactions.get(0);
        assertEquals(LocalDate.of(2024, 7, 1), t1.getTransactionDate());
        assertEquals("Amazon", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("ゴールドポイントカードプラス", t1.getCardName());

        CreditCardTransaction t2 = transactions.get(1);
        assertEquals(LocalDate.of(2024, 7, 5), t2.getTransactionDate());
        assertEquals("Store", t2.getStoreName());
        assertEquals(2000, t2.getAmount());
        assertEquals("ゴールドポイントカードプラス", t2.getCardName());

        assertEquals("2024-07", statement.getBillingMonth());
        assertEquals(136, statement.getTotalAmount());
    }

    @Test
    void parseLines_ExceptionsAndEdgeCases(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("unknown.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("1111,name,other_card\n"); // Card name will be fallback
            writer.write("2024/07/01,Store,BadAmount\n"); // Invalid amount col 2
            writer.write("2024/07/02,Store,x,x,x,x,BadAmount\n"); // Invalid amount col 6
            writer.write("2024/07/03,Store,x,x,x,x\n"); // Length 6, amount not in col 2
            writer.write(" , , , , ,BadTotal,\n"); // Total amount parse fail (handled inside matches? No matches needs
                                                   // digit)
            // What if total has digits but exceeds int?
            writer.write(" , , , , ,999999999999999999999,\n"); // NumberFormatException inside try block

            // To cover L99 branches:
            writer.write(" ,Store,1回払い\n"); // cols.length < 6 (length 3), cols[0] is empty
            writer.write(" ,Store, , , ,136,\n"); // cols.length >= 6, cols[1] is not empty
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(0, transactions.size());

        List<String> failedRows = result.getFailedRows();
        assertEquals(3, failedRows.size()); // The 3 rows with bad amount/format should fail
        assertTrue(failedRows.get(0).contains("BadAmount"));
        assertTrue(failedRows.get(1).contains("BadAmount"));
        assertTrue(failedRows.get(2).contains("x"));
    }

    @Test
    void parseLines_ShortHeader(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("short_header.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("1111\n"); // First line has cols.length < 3
            writer.write("2024/07/01,Amazon,1000\n");
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();
        assertEquals(1, statement.getTransactions().size());
        assertEquals("Gold Point Card+", statement.getTransactions().get(0).getCardName()); // fallback card name
    }
}
