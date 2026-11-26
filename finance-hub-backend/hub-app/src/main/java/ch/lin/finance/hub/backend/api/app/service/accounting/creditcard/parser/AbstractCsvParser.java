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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParser;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Base abstract implementation of {@link CreditCardParser} providing common
 * utilities for parsing CSV-based credit card statements. It handles file
 * encoding
 * detection, metadata extraction (such as billing month from filenames), and
 * safe transaction parsing.
 */
public abstract class AbstractCsvParser implements CreditCardParser {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected static final String DEFAULT_ENCODING = "Windows-31J";

    /**
     * Holds statement-level metadata extracted during parsing, such as payment
     * date,
     * total billed amount, and billing month.
     */
    protected static class StatementMetadata {

        public LocalDate paymentDate;
        public Integer totalAmount;
        public String billingMonth;
        public String cardName;
    }

    /**
     * Returns the name of the parser for logging purposes.
     */
    protected abstract String getParserName();

    /**
     * Determines the encoding for the file. Can be overridden if detection is
     * needed.
     */
    protected String getEncoding(File file) {
        return DEFAULT_ENCODING;
    }

    /**
     * Helper method to detect the correct encoding of a file by reading the
     * first line and testing it against a given predicate.
     */
    protected String detectEncoding(File file, String[] encodings, Predicate<String> headerMatcher,
            String defaultEncoding) {
        for (String encoding : encodings) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), encoding))) {
                String firstLine = reader.readLine();
                if (firstLine != null && headerMatcher.test(firstLine)) {
                    return encoding;
                }
            } catch (IOException e) {
                // Ignore and try the next encoding
            }
        }
        return defaultEncoding;
    }

    /**
     * Helper method to extract the billing month (yyyy-MM) from a filename
     * using a regex. The regex must contain two capturing groups: one for the
     * year (4 digits) and one for the month.
     */
    protected void tryExtractBillingMonthFromFilename(File file, StatementMetadata metadata, String regex) {
        if (file != null) {
            Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(file.getName());
            if (matcher.find() && matcher.groupCount() >= 2) {
                String year = matcher.group(1);
                String month = matcher.group(2).length() < 2 ? "0" + matcher.group(2) : matcher.group(2);
                metadata.billingMonth = year + "-" + month;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CreditCardParseResult parse(File file) throws Exception {
        String dirName = file.getParentFile() != null ? file.getParentFile().getName() : "";
        logger.info("Parsing {} CSV file: {}/{}", getParserName(), dirName, file.getName());
        List<CreditCardTransaction> transactions = new ArrayList<>();
        List<String> failedRows = new ArrayList<>();
        StatementMetadata metadata = new StatementMetadata();

        String encoding = getEncoding(file);
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            logger.warn("Unsupported encoding '{}' detected for file {}. Falling back to UTF-8.", encoding,
                    file.getName());
            charset = StandardCharsets.UTF_8;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset))) {
            parseLines(reader, transactions, failedRows, file, metadata);
        }

        if (failedRows.isEmpty()) {
            logger.info("Successfully parsed {} transactions from {} file.", transactions.size(), getParserName());
        }

        CreditCardStatement statement;
        if (!transactions.isEmpty()) {
            Integer totalAmount = metadata.totalAmount;
            if (totalAmount == null) {
                totalAmount = transactions.stream()
                        .map(t -> t.getAmount())
                        .mapToInt(amt -> amt == null ? 0 : amt)
                        .sum();
            }

            // Resolve billing month: Metadata (Filename/Header) > Payment Date
            String resolvedBillingMonth = "UNKNOWN";
            if (metadata.billingMonth != null) {
                resolvedBillingMonth = metadata.billingMonth;
            } else if (metadata.paymentDate != null) {
                resolvedBillingMonth = metadata.paymentDate
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            }

            String resolvedCardName = metadata.cardName;
            if (resolvedCardName == null) {
                // Fallback to parent directory name if available, otherwise parser name
                File parentFile = file.getParentFile();
                resolvedCardName = parentFile != null ? parentFile.getName() : getParserName();
                logger.info("Card name not explicitly found in {}, forced fallback to: {}", file.getName(),
                        resolvedCardName);
            }

            statement = new CreditCardStatement(
                    resolvedCardName,
                    resolvedBillingMonth,
                    metadata.paymentDate,
                    totalAmount);

            for (CreditCardTransaction tx : transactions) {
                statement.addTransaction(tx);
            }
        } else {
            statement = new CreditCardStatement(
                    getParserName(),
                    "UNKNOWN",
                    null,
                    0);
        }

        return new CreditCardParseResult(statement, failedRows, charset);
    }

    /**
     * Concrete parsers implement this to iterate over the reader and add
     * successfully parsed transactions to the list, or failed raw lines to
     * failedRows.
     */
    protected abstract void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions,
            List<String> failedRows, File file, StatementMetadata metadata) throws Exception;

    /**
     * Standard utility to split a CSV row.
     */
    protected String[] splitCsv(String line) {
        return line.split(",", -1);
    }

    /**
     * Standard utility to split a CSV row that wraps fields in quotes.
     */
    protected String[] splitCsvWithQuotes(String line) {
        String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        for (int i = 0; i < cols.length; i++) {
            cols[i] = cols[i].replaceAll("^\"|\"$", "").trim();
        }
        return cols;
    }

    /**
     * Standard utility to validate and reject non-transaction rows based on a
     * date regex pattern.
     */
    protected boolean isValidTransactionRow(String dateStr, String dateRegex, String line, File file) {
        if (dateStr == null || dateStr.isEmpty() || !dateStr.matches(dateRegex)) {
            logger.warn("Ignored non-transaction row in file {}: {}", file.getName(), line);
            return false;
        }
        return true;
    }

    /**
     * Utility to execute a parsing block and safely catch and record format
     * errors.
     */
    protected void tryParseTransaction(String line, List<String> failedRows, File file, Runnable parserBlock) {
        try {
            parserBlock.run();
        } catch (DateTimeParseException | NumberFormatException | IndexOutOfBoundsException e) {
            logger.warn("Failed to parse {} transaction row in file {}: {}. Exception: {}", getParserName(),
                    file.getName(), line, e.getMessage());

            // Append the error reason to the end of the line, escaping any double quotes
            // for standard CSV format
            String reason = e.getMessage() != null ? e.getMessage().replace("\"", "\"\"") : "Unknown Error";
            failedRows.add(line + ",\"" + reason + "\"");
        }
    }
}
