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
 * Parser implementation for Yahoo! JAPAN Card CSV statements.
 * Differentiates from similar formats like Rakuten Card by checking for the
 * "支払区分" header.
 */
@Component
public class YahooJapanCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for Yahoo! JAPAN Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "Yahoo! JAPAN";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // "支払区分" differentiates Yahoo! JAPAN from Rakuten (which uses "支払方法")
        return firstLine != null && firstLine.contains("利用日") && firstLine.contains("利用金額")
                && firstLine.contains("支払区分");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean isFirstLine = true;

        // Infer billing month from filename (e.g. detail202008(4601).csv -> 2020-08)
        tryExtractBillingMonthFromFilename(file, metadata, "(\\d{4})(\\d{2}).*\\.csv");

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(file.getName());
        if (metadata.cardName == null) {
            if (matcher.find()) {
                metadata.cardName = "Yahoo! JAPAN Card " + matcher.group(1);
            } else {
                metadata.cardName = "Yahoo! JAPAN Card";
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
                Integer amount = Integer.valueOf(cols[4].replace(",", ""));
                String memo = cols[3].isEmpty() ? null : cols[3];

                CreditCardTransaction transaction = new CreditCardTransaction(
                        "Yahoo! JAPAN Card",
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
