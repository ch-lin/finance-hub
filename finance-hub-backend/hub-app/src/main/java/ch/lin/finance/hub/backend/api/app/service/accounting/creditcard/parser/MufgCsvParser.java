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
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Parser implementation for MUFG Card CSV statements.
 * Identifies MUFG files using "ご利用金額" and "お支払日" columns and handles
 * Japanese date formats.
 */
@Component
public class MufgCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for MUFG transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "MUFG";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // MUFG CSV headers usually contain "お支払日" and "ご利用金額"
        return firstLine != null && firstLine.contains("ご利用金額") && firstLine.contains("お支払日");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean isFirstLine = true;
        int paymentDateIndex = 1; // Fallback index for "お支払日"

        if (metadata.cardName == null) {
            metadata.cardName = "MUFG Card";
        }

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsvWithQuotes(line);

            if (isFirstLine) {
                isFirstLine = false;
                for (int i = 0; i < cols.length; i++) {
                    if (cols[i].contains("お支払日")) {
                        paymentDateIndex = i;
                    }
                }
                continue; // Skip the header row
            }

            // Skip summary or user category rows like: "","","【 本 人 】","","","","",""
            if (cols.length < 7 || cols[3].isEmpty()) {
                continue;
            }

            if (!isValidTransactionRow(cols[3], "^\\d{4}年\\d{1,2}月\\d{1,2}日$", line, file)) {
                continue;
            }

            // Extract Payment Date from the transaction row
            if (metadata.paymentDate == null && cols.length > paymentDateIndex && !cols[paymentDateIndex].isEmpty()) {
                try {
                    metadata.paymentDate = LocalDate.parse(cols[paymentDateIndex], DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    logger.warn("Could not parse payment date from MUFG row: {}", cols[paymentDateIndex]);
                }
            }

            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = LocalDate.parse(cols[3], DATE_FORMATTER);
                String storeName = cols[2];
                Integer amount = Integer.valueOf(cols[6].replace(",", ""));
                String memo = cols.length > 7 ? cols[7] : null;

                CreditCardTransaction transaction = new CreditCardTransaction(
                        "MUFG",
                        transactionDate,
                        storeName,
                        amount);
                transaction.setMemo(memo);
                transactions.add(transaction);
            });
        }
    }
}
