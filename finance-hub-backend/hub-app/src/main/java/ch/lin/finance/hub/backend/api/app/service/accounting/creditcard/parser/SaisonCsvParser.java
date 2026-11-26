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
import java.util.Objects;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Parser implementation for SAISON Card CSV statements.
 * Parses dynamic card names and provides a fallback mechanism for annual fees
 * which often lack a transaction date.
 */
@Component
public class SaisonCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for SAISON Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "SAISON";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // "カード名称" is "Card Name" in Japanese
        return firstLine != null && firstLine.contains("カード名称");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        String cardName = "SAISON Card"; // Fallback default
        LocalDate paymentDate = null;

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsv(line);

            // Dynamically extract the specific SAISON card name from the first line
            if (cols.length >= 2 && "カード名称".equals(cols[0])) {
                cardName = cols[1].trim();
                metadata.cardName = cardName;
                continue;
            }

            // Extract the payment date to use as a fallback for annual fee rows
            if (cols.length >= 2 && "お支払日".equals(cols[0])) {
                try {
                    paymentDate = LocalDate.parse(cols[1].trim(), DATE_FORMATTER);
                    metadata.paymentDate = paymentDate;
                } catch (DateTimeParseException e) {
                    logger.warn("Could not parse payment date from SAISON header: {}", cols[1]);
                }
                continue;
            }

            // Extract the total amount
            if (cols.length >= 2 && "今回ご請求額".equals(cols[0])) {
                try {
                    metadata.totalAmount = Integer.valueOf(cols[1].replace(",", "").trim());
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse total amount from SAISON header: {}", cols[1]);
                }
                continue;
            }

            // Skip headers, metadata, and summary rows
            if (cols.length < 6) {
                continue;
            }

            String dateStr = cols[0].trim();
            if (dateStr.isEmpty()) {
                // SAISON leaves the date blank for Annual Fees (年会費). Fallback to payment date.
                if (!cols[1].contains("年会費") || paymentDate == null) {
                    continue; // Standard summary or user metadata row
                }
            } else if (!isValidTransactionRow(dateStr, "^\\d{4}/\\d{2}/\\d{2}$", line, file)) {
                continue;
            }

            final String finalCardName = cardName;
            final LocalDate finalPaymentDate = paymentDate;

            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = dateStr.isEmpty() ? Objects.requireNonNull(finalPaymentDate)
                        : LocalDate.parse(dateStr, DATE_FORMATTER);
                String storeName = cols[1];
                Integer amount = Integer.valueOf(cols[5].replace(",", "").trim());
                String memo = cols.length > 6 ? cols[6] : null;

                CreditCardTransaction transaction = new CreditCardTransaction(
                        finalCardName,
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
