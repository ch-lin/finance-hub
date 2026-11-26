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
import java.nio.charset.StandardCharsets;
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

class SmbcCsvParserTest {

    private SmbcCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new SmbcCsvParser();
    }

    @Test
    @DisplayName("Should return correct parser name")
    void getParserName() {
        assertEquals("SMBC", parser.getParserName());
    }

    @Test
    @DisplayName("canParse - should return true for valid SMBC header in UTF-8")
    void canParse_ValidUtf8_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("Owner,5334-91**-****-****,Card Name");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return true for valid SMBC header in Win31J")
    void canParse_ValidWin31J_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn(null);
        when(preview.getHeaderWin31J()).thenReturn("Owner,4980-0573-4799-2***,Card Name");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return true for UTF-16LE header interpreted as UTF-8 with null bytes")
    void canParse_Utf16Le_ReturnsTrue() {
        FilePreview preview = mock(FilePreview.class);
        // Simulate UTF-16LE string interpreted as UTF-8 (null byte after each char)
        String utf16Simulated = "O\0w\0n\0e\0r\0,\0" + "5\0" + "3\0" + "3\0" + "4\0" + "-\0" + "9\0" + "1\0" + "*\0"
                + "*\0" + "-\0" + "*\0" + "*\0" + "*\0" + "*\0" + "-\0" + "*\0" + "*\0" + "*\0" + "*\0"
                + ",\0C\0a\0r\0d\0";
        when(preview.getHeaderUtf8()).thenReturn(utf16Simulated);
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return false for invalid header")
    void canParse_Invalid_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("Owner,1234,Card");
        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return false when both headers are null")
    void canParse_Null_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn(null);
        when(preview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should handle header length less than 3 safely")
    void canParse_ShortHeader_ReturnsFalse() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderUtf8()).thenReturn("Owner,5334");
        assertFalse(parser.canParse(preview));
    }

    @Test
    @DisplayName("getEncoding - should detect UTF-8 for typical SMBC file")
    void getEncoding_DetectsCorrectly(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("smbc_test.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("林 様,5334-91**-****-****,My SMBC Card\n");
            writer.write("2024/01/01,Store,1000,,,,\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(StandardCharsets.UTF_8, result.getCharset());
    }

    @Test
    @DisplayName("getEncoding - should reject text with U+FFFD")
    void getEncoding_RejectsInvalidChars(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("smbc_invalid.csv").toFile();
        // Writing \uFFFD in UTF-8
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("林 \uFFFD,5334-91**-****-****,My SMBC Card\n");
        }

        // It will fallback to DEFAULT_ENCODING which is Shift_JIS/Windows-31J
        CreditCardParseResult result = parser.parse(file);
        assertEquals(Charset.forName("Windows-31J"), result.getCharset());
    }

    @Test
    @DisplayName("parseLines - should parse successfully and handle amount parsing logic")
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("202406_smbc.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Name,5334-91**-****-****,Custom Card Name\n"); // Header setting current card name
            writer.write("\n"); // Empty line
            // standard line (mainAmount at index 2)
            writer.write("2024/06/01,Amazon,1000,,,0,some memo\n");
            // mainAmount empty, use altAmount (at mainAmountIdx + 3 = 5)
            writer.write("2024/06/02,Netflix,,,,-1000,,no memo here\n");
            // Store name with commas: "Store, With, Comma" -> mainAmountIdx shifts
            writer.write("2024/06/03,Store,With,Comma,500,,,0,Memo 1,Memo 2\n");
            // Empty date in col 0
            writer.write(" ,Store,500,,,,,\n");
            // Short line
            writer.write("2024/06/04,Store\n");
            // Invalid date
            writer.write("InvalidDate,Store,100,,,,,\n");
            // To cover val.isNumeric && !altValIsNumeric:
            writer.write("2024/06/06,Store,500,x,y,NonNumeric,1000,,,0,Memo\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals("2024-06", statement.getBillingMonth());
        assertEquals(4, statement.getTransactions().size());

        CreditCardTransaction t1 = statement.getTransactions().get(0);
        assertEquals(LocalDate.of(2024, 6, 1), t1.getTransactionDate());
        assertEquals("Amazon", t1.getStoreName());
        assertEquals(1000, t1.getAmount());
        assertEquals("some memo", t1.getMemo());
        assertEquals("Custom Card Name", t1.getCardName());

        CreditCardTransaction t2 = statement.getTransactions().get(1);
        assertEquals(LocalDate.of(2024, 6, 2), t2.getTransactionDate());
        assertEquals("Netflix", t2.getStoreName());
        assertEquals(-1000, t2.getAmount());
        assertEquals(",no memo here", t2.getMemo());

        CreditCardTransaction t3 = statement.getTransactions().get(2);
        assertEquals(LocalDate.of(2024, 6, 3), t3.getTransactionDate());
        assertEquals("Store,With,Comma", t3.getStoreName());
        assertEquals(500, t3.getAmount());
        assertEquals("Memo 1,Memo 2", t3.getMemo());

        CreditCardTransaction t4 = statement.getTransactions().get(3);
        assertEquals(LocalDate.of(2024, 6, 6), t4.getTransactionDate());
        assertEquals("Store,500,x,y,NonNumeric", t4.getStoreName());
        assertEquals(1000, t4.getAmount());
        assertEquals("Memo", t4.getMemo());
    }

    @Test
    @DisplayName("getEncoding - should detect UTF-8 when containing カード but not 様")
    void getEncoding_DetectsCorrectly_CardKeyword(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("smbc_test_card.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Owner,5334-91**-****-****,My SMBC カード\n");
            writer.write("2024/01/01,Store,1000,,,,\n");
        }
        CreditCardParseResult result = parser.parse(file);
        assertEquals(StandardCharsets.UTF_8, result.getCharset());
    }

    @Test
    @DisplayName("getEncoding - should reject if neither 様 nor カード is present")
    void getEncoding_RejectsWithoutKeywords(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("smbc_test_nokeyword.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Owner,5334-91**-****-****,My SMBC Card\n");
        }
        CreditCardParseResult result = parser.parse(file);
        assertEquals(Charset.forName("Windows-31J"), result.getCharset());
    }

    @Test
    @DisplayName("parseLines - should handle invalid transaction cleanly")
    void parseLines_Exception_AddedToFailedRows(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("smbc_fail.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            // Valid row structure but amount is bad string
            writer.write("2024/06/01,Store,NotAnAmount,,,,,\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("NotAnAmount"));
        assertEquals(0, result.getStatement().getTransactions().size());
    }

    @Test
    @DisplayName("parseLines - should extract billing month from filename")
    void parseLines_ExtractsBillingMonth(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("202312_smbc.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("2023/12/01,Store,1000,,,,\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals("2023-12", result.getStatement().getBillingMonth());
        assertEquals(1, result.getStatement().getTransactions().size());
        assertEquals("SMBC Card", result.getStatement().getTransactions().get(0).getCardName()); // Default fallback
    }

    @Test
    @DisplayName("parseLines - handles varying column widths without memo safely")
    void parseLines_NoMemoSafe(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("smbc_nomemo.csv").toFile();
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            // Precisely length to stop before memo
            writer.write("2024/06/01,Store,1000,,,0\n");
        }

        CreditCardParseResult result = parser.parse(file);
        assertEquals(0, result.getFailedRows().size());
        CreditCardTransaction t = result.getStatement().getTransactions().get(0);
        assertNull(t.getMemo());
    }
}
