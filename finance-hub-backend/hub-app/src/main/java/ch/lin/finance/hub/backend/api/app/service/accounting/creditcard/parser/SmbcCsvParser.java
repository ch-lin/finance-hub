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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Parser implementation for SMBC Card CSV statements.
 * Identifies SMBC files by recognizing masked card number patterns in the
 * header
 * and handles potential UTF-16LE encoding anomalies.
 */
@Component
public class SmbcCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for SMBC Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * Regex pattern to match SMBC masked card numbers like 5334-91**-****-**** or
     * 4980-0573-4799-2***.
     */
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^\\d{4}-[\\d*]{4}-[\\d*]{4}-[\\d*]{4}$");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "SMBC";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEncoding(File file) {
        return detectEncoding(file, new String[] { "UTF-8", "Windows-31J", "UTF-16LE" }, firstLine -> {
            if (isSmbcHeader(firstLine)) {
                // Verify there are no decoding errors producing the replacement character
                // (\uFFFD), and it contains common Japanese characters for SMBC ("様" or "カード")
                return !firstLine.contains("\uFFFD") && (firstLine.contains("様") || firstLine.contains("カード"));
            }
            return false;
        }, DEFAULT_ENCODING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        if (isSmbcHeader(preview.getHeaderUtf8()) || isSmbcHeader(preview.getHeaderWin31J())) {
            return true;
        }

        // Handle UTF-16LE files encoded as UTF-8/Win31J in the preview.
        // ASCII characters will have interleaved null bytes (\0), so we can strip them
        // to test the regex.
        if (preview.getHeaderUtf8() != null) {
            String cleanedHeader = preview.getHeaderUtf8().replace("\0", "");
            if (isSmbcHeader(cleanedHeader)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the header matches the SMBC Card format.
     *
     * @param header The header line to check.
     * @return True if it matches SMBC Card format, false otherwise.
     */
    private boolean isSmbcHeader(String header) {
        if (header != null) {
            String[] cols = header.split(",", -1);
            // The second column of the first row should be the masked card number
            return cols.length >= 3 && CARD_NUMBER_PATTERN.matcher(cols[1].trim()).matches();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        String currentCardName = "SMBC Card"; // Fallback default

        // Infer billing month from filename (e.g. 202406.csv -> 2024-06)
        tryExtractBillingMonthFromFilename(file, metadata, "(\\d{4})(\\d{2}).*\\.csv");

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsv(line);

            // Check if this row is a card header (Name, Card Number, Card Name)
            if (cols.length >= 3 && CARD_NUMBER_PATTERN.matcher(cols[1].trim()).matches()) {
                currentCardName = cols[2].trim();
                if (metadata.cardName == null)
                    metadata.cardName = currentCardName;
                continue;
            }

            // Valid transaction rows have a date in the first column and at least an amount
            // in column 2 or 5
            if (cols.length >= 6) {
                if (cols[0].trim().isEmpty()) {
                    continue;
                }

                String dateStr = cols[0].trim();
                if (!isValidTransactionRow(dateStr, "^\\d{4}/\\d{2}/\\d{2}$", line, file)) {
                    continue;
                }

                final String finalCardName = currentCardName;

                tryParseTransaction(line, failedRows, file, () -> {
                    LocalDate transactionDate = LocalDate.parse(dateStr, DATE_FORMATTER);

                    int mainAmountIdx = 2;
                    StringBuilder storeNameBuilder = new StringBuilder(cols[1].trim());

                    while (mainAmountIdx < cols.length - 3) {
                        String val = cols[mainAmountIdx].trim();
                        String altVal = cols[mainAmountIdx + 3].trim();

                        boolean valIsNumeric = val.matches("^-?\\d+$");
                        boolean altValIsNumeric = altVal.matches("^-?\\d+$");

                        if ((val.isEmpty() || valIsNumeric) && (altVal.isEmpty() || altValIsNumeric)) {
                            if (valIsNumeric || altValIsNumeric) {
                                break;
                            }
                        }

                        storeNameBuilder.append(",").append(cols[mainAmountIdx]);
                        mainAmountIdx++;
                    }

                    String storeName = storeNameBuilder.toString().trim();
                    int altAmountIdx = mainAmountIdx + 3;
                    int memoIdx = mainAmountIdx + 4;

                    String amountStr = cols[mainAmountIdx].trim().isEmpty() ? cols[altAmountIdx].trim()
                            : cols[mainAmountIdx].trim();
                    Integer amount = Integer.valueOf(amountStr.replace(",", ""));

                    StringBuilder memoBuilder = new StringBuilder();
                    for (int i = memoIdx; i < cols.length; i++) {
                        if (i > memoIdx) {
                            memoBuilder.append(",");
                        }
                        memoBuilder.append(cols[i]);
                    }
                    String memo = memoBuilder.toString().trim();
                    if (memo.isEmpty()) {
                        memo = null;
                    }

                    CreditCardTransaction transaction = new CreditCardTransaction(
                            finalCardName,
                            transactionDate,
                            storeName,
                            amount);
                    transaction.setMemo(memo);
                    transactions.add(transaction);
                });
            }
        }
    }
}
