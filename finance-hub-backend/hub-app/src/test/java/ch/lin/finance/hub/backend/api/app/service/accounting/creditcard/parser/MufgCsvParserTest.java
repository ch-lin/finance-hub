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

public class MufgCsvParserTest {

    private MufgCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new MufgCsvParser();
    }

    @Test
    void getParserName_ReturnsCorrectName() {
        assertEquals("MUFG", parser.getParserName());
    }

    @Test
    void canParse_ValidPreview_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("何か,お支払日,ご利用金額,その他");
        assertTrue(parser.canParse(preview));
    }

    @Test
    void canParse_InvalidPreview_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("ご利用金額,その他"); // Missing お支払日
        assertFalse(parser.canParse(preview));

        when(preview.getHeaderWin31J()).thenReturn("お支払日,その他"); // Missing ご利用金額
        assertFalse(parser.canParse(preview));

        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("mufg202407.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("dummy,お支払日,Store,Date,dummy,dummy,Amount,Memo\n"); // Header, "お支払日" is at index 1
            writer.write("\n"); // Empty
            writer.write("\"A\",\"2024年8月10日\",\"Amazon\",\"2024年7月1日\",\"D\",\"E\",\"1,000\",\"memo1\"\n");
            // No memo (length <= 7)
            writer.write("\"A\",\"2024年8月10日\",\"Store2\",\"2024年7月5日\",\"D\",\"E\",\"2000\"\n");
            // Length < 7
            writer.write("\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"\n");
            // Empty date (cols[3] is empty)
            writer.write("\"A\",\"B\",\"C\",\"\",\"D\",\"E\",\"2000\"\n");
            // Invalid date
            writer.write("\"A\",\"B\",\"C\",\"BadDate\",\"D\",\"E\",\"2000\"\n");
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(2, transactions.size());

        CreditCardTransaction t1 = transactions.get(0);
        assertEquals(LocalDate.of(2024, 7, 1), t1.getTransactionDate());
        assertEquals("Amazon", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("MUFG", t1.getCardName());
        assertEquals("memo1", t1.getMemo());

        CreditCardTransaction t2 = transactions.get(1);
        assertEquals(LocalDate.of(2024, 7, 5), t2.getTransactionDate());
        assertEquals("Store2", t2.getStoreName());
        assertEquals(2000, t2.getAmount());
        assertNull(t2.getMemo());

        // The first valid payment date sets the metadata
        assertEquals(LocalDate.of(2024, 8, 10), statement.getPaymentDate());
    }

    @Test
    void parseLines_ExceptionsAndEdgeCases(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("mufg_bad.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            // Header: "お支払日" is at index 1
            writer.write("dummy,お支払日,Store,Date,dummy,dummy,Amount,Memo\n");
            // Empty payment date -> tests !cols[paymentDateIndex].isEmpty() returning false
            writer.write("\"A\",\"\",\"Store0\",\"2024年7月3日\",\"D\",\"E\",\"3000\",\"memo0\"\n");
            // Bad payment date -> tests DateTimeParseException block since
            // metadata.paymentDate is still null
            writer.write("\"A\",\"BadPaymentDate\",\"Store2\",\"2024年7月2日\",\"D\",\"E\",\"2000\",\"memo2\"\n");
            // Bad amount
            writer.write("\"A\",\"2024年8月10日\",\"Store1\",\"2024年7月1日\",\"D\",\"E\",\"BadAmount\",\"memo1\"\n");
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(2, transactions.size()); // The first two transactions parse successfully!

        CreditCardTransaction t1 = transactions.get(0);
        assertEquals(LocalDate.of(2024, 7, 3), t1.getTransactionDate());
        assertEquals(3000, t1.getAmount());

        CreditCardTransaction t2 = transactions.get(1);
        assertEquals(LocalDate.of(2024, 7, 2), t2.getTransactionDate());
        assertEquals(2000, t2.getAmount());

        List<String> failedRows = result.getFailedRows();
        assertEquals(1, failedRows.size()); // The row with bad amount fails
        assertTrue(failedRows.get(0).contains("BadAmount"));

        // Payment date is only successfully extracted on the third row (bad amount row,
        // but payment date is parsed BEFORE amount)
        // Actually, the bad amount row is parsed, payment date is extracted, then
        // tryParseTransaction catches the bad amount.
        // So the statement will have the payment date!
        assertEquals(LocalDate.of(2024, 8, 10), statement.getPaymentDate());
    }

    @Test
    void parseLines_PaymentDateIndexOutOfBounds(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("mufg_out_of_bounds.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            // Header: "お支払日" is at index 10
            writer.write("0,1,2,3,4,5,6,7,8,9,お支払日\n");
            // Transaction: only 7 columns, so cols.length (7) is NOT > paymentDateIndex
            // (10)
            writer.write("\"0\",\"1\",\"Store\",\"2024年7月1日\",\"4\",\"5\",\"1000\"\n");
        }

        CreditCardParseResult result = parser.parse(file);
        CreditCardStatement statement = result.getStatement();

        List<CreditCardTransaction> transactions = statement.getTransactions();
        assertEquals(1, transactions.size());
        assertNull(statement.getPaymentDate());
    }
}
