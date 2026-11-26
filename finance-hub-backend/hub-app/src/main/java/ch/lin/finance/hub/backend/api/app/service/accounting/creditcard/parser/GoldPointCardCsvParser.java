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
 * Parser implementation for Yodobashi Gold Point Card+ CSV statements.
 * Identifies the file format via the "ゴールドポイントカード" text in the first row.
 */
@Component
public class GoldPointCardCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for Gold Point Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "Gold Point Card";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // The first line usually contains the cardholder's name, masked card number,
        // and "ゴールドポイントカードプラス"
        return firstLine != null && firstLine.contains("ゴールドポイントカード");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean isFirstLine = true;
        String cardName = "Gold Point Card+"; // Fallback default

        // Infer billing month from filename (e.g. 202402.csv -> 2024-02)
        tryExtractBillingMonthFromFilename(file, metadata, "(\\d{4})(\\d{2})(?:\\d{2})?.*\\.csv");

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsv(line);

            if (isFirstLine) {
                isFirstLine = false;
                // Dynamically extract the specific card name from the first line
                if (cols.length >= 3 && cols[2].contains("ゴールドポイントカード")) {
                    cardName = cols[2].trim();
                    if (metadata.cardName == null)
                        metadata.cardName = cardName;
                    continue;
                }
                continue;
            }

            if (cols.length < 3) {
                continue;
            }
            if (cols[0].trim().isEmpty()) {
                // Attempt to parse the summary row for total amount (e.g. ",,,,,136,")
                if (cols.length >= 6 && cols[1].trim().isEmpty()) {
                    String maybeTotal = cols[5].trim();
                    if (maybeTotal.matches("^-?[0-9,]+$")) {
                        try {
                            metadata.totalAmount = Integer.valueOf(maybeTotal.replace(",", ""));
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse total amount from Gold Point footer: {}", maybeTotal);
                        }
                    }
                }
                continue;
            }

            String dateStr = cols[0].trim();
            if (!isValidTransactionRow(dateStr, "^\\d{4}/\\d{2}/\\d{2}$", line, file)) {
                continue;
            }

            final String finalCardName = cardName;

            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                String storeName = cols[1].trim();

                Integer amount;

                // Handle two different CSV formats observed for Gold Point Card
                if (cols[2].replace(",", "").trim().matches("^-?\\d+$")) {
                    amount = Integer.valueOf(cols[2].replace(",", "").trim());
                } else if (cols.length >= 7 && cols[6].replace(",", "").trim().matches("^-?\\d+$")) {
                    amount = Integer.valueOf(cols[6].replace(",", "").trim());
                } else {
                    throw new NumberFormatException("Could not find amount column");
                }

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
