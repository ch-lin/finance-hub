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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

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
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

class AbstractCsvParserTest {

    private DummyParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new DummyParser();
    }

    @Test
    @DisplayName("Should parse basic file successfully and aggregate totals")
    void parse_ValidFile_ReturnsResult(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("dummy_file.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("UTF-8"))) {
            writer.write("dummy line\n");
        }

        CreditCardParseResult result = parser.parse(csvFile);

        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertNotNull(statement);
        assertEquals("DummyParser", statement.getCardName());
        // Since we didn't specify metadata.totalAmount, it should sum up transactions
        assertEquals(150, statement.getTotalAmount());
        assertEquals("UNKNOWN", statement.getBillingMonth());
        assertEquals(3, statement.getTransactions().size());
    }

    @Test
    @DisplayName("Should resolve billing month from payment date if filename regex misses")
    void parse_ResolvesBillingMonthFromPaymentDate(@TempDir Path tempDir) throws Exception {
        parser.setSimulatePaymentDate(LocalDate.of(2023, 5, 20));
        File csvFile = tempDir.resolve("dummy_file.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("UTF-8"))) {
            writer.write("dummy line\n");
        }

        CreditCardParseResult result = parser.parse(csvFile);

        CreditCardStatement statement = result.getStatement();
        assertEquals("2023-05", statement.getBillingMonth());
        assertEquals(LocalDate.of(2023, 5, 20), statement.getPaymentDate());
    }

    @Test
    @DisplayName("Should detect encoding correctly or fallback")
    void detectEncoding_WorksAsExpected(@TempDir Path tempDir) throws Exception {
        File csvFile = tempDir.resolve("encoding.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("Windows-31J"))) {
            writer.write("テストヘッダー\n");
        }

        String[] encodings = { "UTF-8", "Windows-31J", "Shift_JIS" };
        String encoding = parser.testDetectEncoding(csvFile, encodings, line -> line.contains("テストヘッダー"), "UTF-8");
        assertEquals("Windows-31J", encoding);

        // Test fallback
        String fallback = parser.testDetectEncoding(csvFile, encodings, line -> line.contains("NONEXISTENT"), "UTF-8");
        assertEquals("UTF-8", fallback);

        // Test empty file
        File emptyFile = tempDir.resolve("empty.csv").toFile();
        emptyFile.createNewFile();
        String emptyFallback = parser.testDetectEncoding(emptyFile, encodings, line -> true, "UTF-8");
        assertEquals("UTF-8", emptyFallback);
    }

    @Test
    @DisplayName("Should handle missing file in detectEncoding")
    void detectEncoding_MissingFile_ReturnsDefault() {
        String[] encodings = { "UTF-8" };
        String fallback = parser.testDetectEncoding(new File("non_existent_file.csv"), encodings, line -> true,
                "DefaultEncoding");
        assertEquals("DefaultEncoding", fallback);
    }

    @Test
    @DisplayName("Should split CSV with quotes correctly")
    void splitCsvWithQuotes_WorksAsExpected() {
        String line = "\"Col 1\",\"Col, 2\",Col 3,\" Col 4 \"";
        String[] cols = parser.testSplitCsvWithQuotes(line);
        assertEquals(4, cols.length);
        assertEquals("Col 1", cols[0]);
        assertEquals("Col, 2", cols[1]);
        assertEquals("Col 3", cols[2]);
        assertEquals("Col 4", cols[3]);
    }

    @Test
    @DisplayName("Should use UTF-8 if fallback fails")
    void parse_ValidEncoding_UsesFallback(@TempDir Path tempDir) throws Exception {
        parser.setSimulateEncoding("UTF-8");
        File csvFile = tempDir.resolve("dummy_file.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("UTF-8"))) {
            writer.write("dummy line\n");
        }

        CreditCardParseResult result = parser.parse(csvFile);
        assertNotNull(result);
        assertEquals(Charset.forName("UTF-8"), result.getCharset());
    }

    @Test
    @DisplayName("Should fallback to UTF-8 on invalid encoding")
    void parse_InvalidEncoding_UsesFallback(@TempDir Path tempDir) throws Exception {
        parser.setSimulateEncoding("INVALID-ENCODING");
        File csvFile = tempDir.resolve("dummy_file.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("UTF-8"))) {
            writer.write("dummy line\n");
        }

        CreditCardParseResult result = parser.parse(csvFile);
        assertNotNull(result);
        assertEquals(StandardCharsets.UTF_8, result.getCharset());
    }

    @Test
    @DisplayName("Should handle parsing exceptions with null message in tryParseTransaction")
    void tryParseTransaction_NullMessage() {
        List<String> failedRows = new java.util.ArrayList<>();
        File dummyFile = new File("dummy.csv");

        parser.testTryParseTransaction("bad,line", failedRows, dummyFile, () -> {
            throw new IndexOutOfBoundsException(); // Default constructor has null message
        });

        assertEquals(1, failedRows.size());
        assertTrue(failedRows.get(0).contains("Unknown Error"));
    }

    @Test
    @DisplayName("Should handle empty file/transactions safely")
    void parse_EmptyTransactions_SafelyHandled(@TempDir Path tempDir) throws Exception {
        parser.setSimulateEmpty(true);
        File csvFile = tempDir.resolve("dummy_file.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile, Charset.forName("UTF-8"))) {
            writer.write("dummy line\n");
        }

        CreditCardParseResult result = parser.parse(csvFile);
        assertNotNull(result);
        CreditCardStatement statement = result.getStatement();
        assertEquals(0, statement.getTotalAmount());
        assertEquals("UNKNOWN", statement.getBillingMonth());
        assertTrue(statement.getTransactions().isEmpty());
    }

    @Test
    @DisplayName("Should extract billing month from filename")
    void tryExtractBillingMonthFromFilename_WorksAsExpected() {
        AbstractCsvParser.StatementMetadata metadata = new AbstractCsvParser.StatementMetadata();
        parser.testTryExtractBillingMonthFromFilename(new File("20230812_file.csv"), metadata,
                "(\\d{4})(\\d{2})(?:\\d{2})?.*\\.csv");
        assertEquals("2023-08", metadata.billingMonth);

        // Single digit month
        parser.testTryExtractBillingMonthFromFilename(new File("20235_file.csv"), metadata,
                "(\\d{4})(\\d{1,2}).*\\.csv");
        assertEquals("2023-05", metadata.billingMonth);

        // No match
        metadata.billingMonth = null;
        parser.testTryExtractBillingMonthFromFilename(new File("random.csv"), metadata,
                "(\\d{4})(\\d{2})(?:\\d{2})?.*\\.csv");
        assertNull(metadata.billingMonth);

        // Null file
        parser.testTryExtractBillingMonthFromFilename(null, metadata, "(\\d{4})(\\d{2}).*\\.csv");
        assertNull(metadata.billingMonth);

        // Group count < 2
        parser.testTryExtractBillingMonthFromFilename(new File("2023_file.csv"), metadata, "(\\d{4}).*\\.csv");
        assertNull(metadata.billingMonth);
    }

    @Test
    @DisplayName("Should validate transaction row with regex")
    void isValidTransactionRow_WorksAsExpected() {
        assertTrue(parser.testIsValidTransactionRow("2023-01-01", "^\\d{4}-\\d{2}-\\d{2}$", "line", new File("f")));
        assertFalse(parser.testIsValidTransactionRow("InvalidDate", "^\\d{4}-\\d{2}-\\d{2}$", "line", new File("f")));
        assertFalse(parser.testIsValidTransactionRow("", "^\\d{4}-\\d{2}-\\d{2}$", "line", new File("f")));
        assertFalse(parser.testIsValidTransactionRow(null, "^\\d{4}-\\d{2}-\\d{2}$", "line", new File("f")));
    }

    private static class DummyParser extends AbstractCsvParser {

        private String simulateEncoding = "UTF-8";
        private LocalDate simulatePaymentDate = null;
        private boolean simulateEmpty = false;

        public void setSimulateEncoding(String simulateEncoding) {
            this.simulateEncoding = simulateEncoding;
        }

        public void setSimulatePaymentDate(LocalDate simulatePaymentDate) {
            this.simulatePaymentDate = simulatePaymentDate;
        }

        public void setSimulateEmpty(boolean simulateEmpty) {
            this.simulateEmpty = simulateEmpty;
        }

        @Override
        protected String getParserName() {
            return "DummyParser";
        }

        @Override
        public boolean canParse(FilePreview preview) {
            return true;
        }

        @Override
        protected String getEncoding(File file) {
            return simulateEncoding;
        }

        @Override
        protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions,
                List<String> failedRows, File file, StatementMetadata metadata) throws Exception {
            metadata.cardName = "DummyParser";
            metadata.paymentDate = simulatePaymentDate;

            if (simulateEmpty) {
                return;
            }
            if (!simulateEmpty) {
                transactions.add(new CreditCardTransaction("DummyCard", LocalDate.now(), "Store 1", 50));
                transactions.add(new CreditCardTransaction("DummyCard", LocalDate.now(), "Store 2", 100));
                transactions.add(new CreditCardTransaction("DummyCard", LocalDate.now(), "Store 3", null));
            }
        }

        // Expose protected methods for testing
        public String testDetectEncoding(File file, String[] encodings,
                java.util.function.Predicate<String> headerMatcher, String defaultEncoding) {
            return detectEncoding(file, encodings, headerMatcher, defaultEncoding);
        }

        public String[] testSplitCsvWithQuotes(String line) {
            return splitCsvWithQuotes(line);
        }

        public void testTryExtractBillingMonthFromFilename(File file, StatementMetadata metadata, String regex) {
            tryExtractBillingMonthFromFilename(file, metadata, regex);
        }

        public boolean testIsValidTransactionRow(String dateStr, String dateRegex, String line, File file) {
            return isValidTransactionRow(dateStr, dateRegex, line, file);
        }

        public void testTryParseTransaction(String line, List<String> failedRows, File file, Runnable parserBlock) {
            tryParseTransaction(line, failedRows, file, parserBlock);
        }
    }
}
