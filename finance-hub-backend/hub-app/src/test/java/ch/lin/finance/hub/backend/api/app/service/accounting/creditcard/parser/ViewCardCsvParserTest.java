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
import static org.junit.jupiter.api.Assertions.assertNull;
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

public class ViewCardCsvParserTest {

    private ViewCardCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new ViewCardCsvParser();
    }

    @Test
    void getParserName_ReturnsCorrectName() {
        assertEquals("View Card", parser.getParserName());
    }

    @Test
    void canParse_ValidPreview_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("会員番号,1234");
        assertTrue(parser.canParse(preview));
    }

    @Test
    void canParse_InvalidPreview_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("他の項目"); // Missing 会員番号
        assertFalse(parser.canParse(preview));

        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("viewcard.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("会員番号,1234\n");
            writer.write("\n"); // Empty
            writer.write("\"対象カード\",\"「ビュー・スイカ」カード\"\n");
            writer.write("\"お支払日\",\"2024年8月15日\"\n");
            writer.write("\"今回お支払金額\",\"1,500\"\n");
            // Not enough columns
            writer.write("\"2024/07/01\",\"Store\"\n");
            // 1 column to test length < 2 branch
            writer.write("\"JustOneColumn\"\n");
            // Empty date
            writer.write("\" \",\"Store\",\"1000\"\n");
            // Invalid date
            writer.write("\"BadDate\",\"Store\",\"1000\"\n");
            // Valid transactions
            writer.write("\"2024/7/1\",\"Store A\",\"1,000\"\n");
            writer.write("\"2024/07/05\",\"Store B\",\"500\"\n");
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        assertEquals(LocalDate.of(2024, 8, 15), statement.getPaymentDate());
        assertEquals(1500, statement.getTotalAmount());

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(2, transactions.size());

        CreditCardTransaction t1 = transactions.get(0);
        assertEquals(LocalDate.of(2024, 7, 1), t1.getTransactionDate());
        assertEquals("Store A", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("ビュー・スイカカード", t1.getCardName()); // "「", "」", "｣" removed

        CreditCardTransaction t2 = transactions.get(1);
        assertEquals(LocalDate.of(2024, 7, 5), t2.getTransactionDate());
        assertEquals("Store B", t2.getStoreName());
        assertEquals(500, t2.getAmount());
    }

    @Test
    void parseLines_ExceptionsAndEdgeCases(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("viewcard_bad.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            // Bad payment date
            writer.write("\"お支払日\",\"BadPaymentDate\"\n");
            // Bad total amount
            writer.write("\"今回お支払金額\",\"BadAmount\"\n");
            // Bad transaction amount
            writer.write("\"2024/7/1\",\"Store A\",\"BadAmount\"\n");
            // Without 対象カード line, should default to "View Card"
            writer.write("\"2024/7/2\",\"Store B\",\"200\"\n");
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        assertNull(statement.getPaymentDate());
        assertEquals(200, statement.getTotalAmount());

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(1, transactions.size()); // Store B succeeds

        CreditCardTransaction t1 = transactions.get(0);
        assertEquals("View Card", t1.getCardName());

        List<String> failedRows = result.getFailedRows();
        assertEquals(1, failedRows.size());
        assertTrue(failedRows.get(0).contains("BadAmount")); // The Store A row
    }
}
