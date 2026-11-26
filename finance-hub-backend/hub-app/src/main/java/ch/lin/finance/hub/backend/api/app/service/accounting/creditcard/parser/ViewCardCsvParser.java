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
 * Parser implementation for View Card (e.g., View Suica) CSV statements.
 * Identifies the file format via the "会員番号" header and extracts the dynamic
 * card name.
 */
@Component
public class ViewCardCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for View Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    /**
     * Date formatter for the payment date in the header.
     */
    private static final DateTimeFormatter PAYMENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "View Card";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // View Card CSV files start with "会員番号"
        return firstLine != null && firstLine.contains("会員番号");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        String cardName = "View Card"; // Fallback default

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsvWithQuotes(line);

            // Dynamically extract the specific View Card name (e.g., 「ビュー・スイカ」カード)
            if (cols.length >= 2 && "対象カード".equals(cols[0])) {
                cardName = cols[1].replace("「", "").replace("」", "").replace("｣", "").trim();
                if (metadata.cardName == null)
                    metadata.cardName = cardName;
                continue;
            }

            // Extract Payment Date
            if (cols.length >= 2 && cols[0].contains("お支払日")) {
                try {
                    metadata.paymentDate = LocalDate.parse(cols[1].trim(), PAYMENT_DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    logger.warn("Could not parse payment date from View Card header: {}", cols[1]);
                }
                continue;
            }

            // Extract Total Amount
            if (cols.length >= 2 && cols[0].contains("今回お支払金額")) {
                try {
                    metadata.totalAmount = Integer.valueOf(cols[1].replace(",", "").trim());
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse total amount from View Card header: {}", cols[1]);
                }
                continue;
            }

            if (cols.length < 3) {
                continue;
            }
            if (cols[0].trim().isEmpty()) {
                continue;
            }

            String dateStr = cols[0].trim();
            if (!isValidTransactionRow(dateStr, "^\\d{4}/\\d{1,2}/\\d{1,2}$", line, file)) {
                continue;
            }

            final String finalCardName = cardName;
            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                String storeName = cols[1];
                // Remove embedded commas inside the amount string
                Integer amount = Integer.valueOf(cols[2].replace(",", ""));

                CreditCardTransaction transaction = new CreditCardTransaction(
                        finalCardName,
                        transactionDate,
                        storeName,
                        amount);
                transactions.add(transaction);
            });
        }
    }
}
