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

class YahooJapanCsvParserTest {

    private YahooJapanCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new YahooJapanCsvParser();
    }

    @Test
    @DisplayName("Should return correct parser name")
    void getParserName() {
        assertEquals("Yahoo! JAPAN", parser.getParserName());
    }

    @Test
    @DisplayName("canParse - should return true for valid Yahoo Japan header in Win31J")
    void canParse_ValidWin31J_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("利用日,利用店名,利用者,支払区分,利用金額");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return false for invalid header")
    void canParse_Invalid_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("利用日,利用店名,利用者,支払方法,利用金額"); // Rakuten style
        assertFalse(parser.canParse(preview));

        when(preview.getHeaderWin31J()).thenReturn("利用日,利用店名,利用者,支払区分"); // Missing 利用金額
        assertFalse(parser.canParse(preview));

        when(preview.getHeaderWin31J()).thenReturn("利用店名,利用者,支払区分,利用金額"); // Missing 利用日
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
        File file = tempDir.resolve("detail202008(4601).csv").toFile();
        try (FileWriter writer = new FileWriter(file, Charset.forName("Windows-31J"))) {
            writer.write("利用日,利用店名,利用者,支払区分,利用金額\n");
            writer.write("\n"); // Empty line

            // Valid row
            writer.write("2020/8/1,Yahoo Shopping,本人,1回払い,3000\n");

            // Valid row with empty memo
            writer.write("2020/8/3,Empty Memo Store,本人,,4000\n");

            // Short row (length < 5)
            writer.write("2020/8/2,Store,本人,1回払い\n");

            // Empty date
            writer.write(",Store,本人,1回払い,1000\n");

            // Invalid date format
            writer.write("InvalidDate,Store,本人,1回払い,1000\n");
        }

        // Simulate reading with default encoding (Windows-31J) but we wrote UTF-8,
        // YahooJapan uses AbstractCsvParser's default which gets File chars.
        // It's okay, because in tests we just use AbstractCsvParser.parse() which uses
        // Windows-31J if nothing matches.
        // For testing parseLines logic, let's just make it parsable. Wait,
        // YahooJapanCsvParser does not override getEncoding()
        // so it defaults to Windows-31J.
        CreditCardParseResult result = parser.parse(file);

        // Assertions
        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals("2020-08", statement.getBillingMonth()); // from filename
        assertEquals(2, statement.getTransactions().size());

        CreditCardTransaction t1 = statement.getTransactions().get(0);
        assertEquals(LocalDate.of(2020, 8, 1), t1.getTransactionDate());
        assertEquals("Yahoo Shopping", t1.getStoreName());
        assertEquals(3000, t1.getAmount());
        assertEquals("1回払い", t1.getMemo());
        assertEquals("Yahoo! JAPAN Card", t1.getCardName());

        CreditCardTransaction t2 = statement.getTransactions().get(1);
        assertEquals(LocalDate.of(2020, 8, 3), t2.getTransactionDate());
        assertEquals("Empty Memo Store", t2.getStoreName());
        assertEquals(4000, t2.getAmount());
        assertNull(t2.getMemo());
        assertEquals("Yahoo! JAPAN Card", t2.getCardName());
    }

    @Test
    @DisplayName("parseLines - should handle parse exception on row")
    void parseLines_Exception_AddedToFailedRows(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("detail_fail.csv").toFile();
        try (FileWriter writer = new FileWriter(file, Charset.forName("Windows-31J"))) {
            writer.write("利用日,利用店名,利用者,支払区分,利用金額\n");
            // Invalid amount
            writer.write("2020/8/1,Store,本人,1回払い,BadAmount\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("BadAmount"));
    }
}
