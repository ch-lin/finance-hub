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

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Parser implementation for Rakuten Card CSV statements.
 * Identifies the format via specific headers like "支払方法" and supports
 * both UTF-8 and Windows-31J encodings.
 */
@Component
public class RakutenCardCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for Rakuten Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "Rakuten Card";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEncoding(File file) {
        return detectEncoding(file, new String[] { "UTF-8", "Windows-31J" }, this::isRakutenHeader, DEFAULT_ENCODING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        return isRakutenHeader(preview.getHeaderUtf8()) || isRakutenHeader(preview.getHeaderWin31J());
    }

    /**
     * Checks if the header matches the Rakuten Card format.
     *
     * @param header The header line to check.
     * @return True if it matches Rakuten Card format, false otherwise.
     */
    private boolean isRakutenHeader(String header) {
        // "支払方法" differentiates Rakuten Card from Yahoo! JAPAN (which uses "支払区分")
        return header != null && header.contains("利用日") && header.contains("利用金額") && header.contains("支払方法");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean isFirstLine = true;

        tryExtractBillingMonthFromFilename(file, metadata, "enavi(\\d{4})(\\d{2})");

        String fileName = file.getName();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(fileName);
        if (metadata.cardName == null) {
            if (matcher.find()) {
                metadata.cardName = "Rakuten Card " + matcher.group(1);
            } else {
                metadata.cardName = "Rakuten Card";
            }
        }

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsvWithQuotes(line);

            if (isFirstLine) {
                isFirstLine = false;
                continue; // Skip the header row
            }

            if (cols.length < 5 || cols[0].isEmpty()) {
                continue;
            }

            if (!isValidTransactionRow(cols[0], "^\\d{4}/\\d{1,2}/\\d{1,2}$", line, file)) {
                continue;
            }

            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = LocalDate.parse(cols[0], DATE_FORMATTER);
                String storeName = cols[1];
                String amountStr = cols[4].replace(",", "").trim();
                Integer amount = amountStr.isEmpty() ? 0 : Integer.valueOf(amountStr);
                String memo = cols[3].trim().isEmpty() ? null : cols[3].trim(); // "支払方法" (Payment method) used as memo
                                                                                // memo

                CreditCardTransaction transaction = new CreditCardTransaction(
                        metadata.cardName,
                        transactionDate,
                        storeName,
                        amount
                );
                transaction.setMemo(memo);
                transactions.add(transaction);
            });
        }
    }
}
