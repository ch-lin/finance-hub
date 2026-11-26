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
 * Parser implementation for JCB Card CSV statements.
 * Handles JCB's unique dynamic card name formatting and quoted CSV structures.
 */
@Component
public class JcbCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for JCB transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "JCB";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // JCB files typically start with the transfer date or payment date header
        return firstLine != null && (firstLine.contains("今回のお振替日") || firstLine.contains("今回のお支払日"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean inTransactionSection = false;

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsvWithQuotes(line);

            if (!inTransactionSection) {
                // Extract Payment Date
                if (cols.length >= 4 && (cols[2].contains("今回のお振替日") || cols[2].contains("今回のお支払日"))) {
                    try {
                        metadata.paymentDate = LocalDate.parse(cols[3], DATE_FORMATTER);
                    } catch (DateTimeParseException e) {
                        logger.warn("Could not parse payment date from JCB header: {}", cols[3]);
                    }
                }

                // Extract Total Amount
                if (cols.length >= 4 && cols[2].contains("合計(円)")) {
                    try {
                        metadata.totalAmount = Integer.valueOf(cols[3].replace(",", ""));
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse total amount from JCB header: {}", cols[3]);
                    }
                }

                // Wait until we hit the header row of the transaction table
                if (cols[0].contains("ご利用者")) {
                    inTransactionSection = true;
                }
                continue;
            }

            // Ignore summary rows at the bottom where the date column is typically empty
            if (cols.length < 5 || cols[2].isEmpty()) {
                continue;
            }

            if (!isValidTransactionRow(cols[2], "^\\d{4}/\\d{1,2}/\\d{1,2}$", line, file)) {
                continue;
            }

            // Dynamic Card Name Extraction
            // E.g., "****-****-****-1019 【ＪＣＢ】カードＷ ＮＬ 林 哲宏 様"
            String cardInfo = cols[0];
            String cardName = "JCB";
            if (cardInfo.contains("【ＪＣＢ】")) {
                int startIndex = cardInfo.indexOf("【ＪＣＢ】") + 5;
                // Split the remainder by full-width spaces " "
                String[] parts = cardInfo.substring(startIndex).split("　");

                // Rejoin the card name parts, ignoring the last 3 parts (Last Name, First Name,
                // "様")
                if (parts.length >= 3) {
                    StringBuilder nameBuilder = new StringBuilder("JCB ");
                    for (int i = 0; i < parts.length - 3; i++) {
                        nameBuilder.append(parts[i]).append(i < parts.length - 4 ? " " : "");
                    }
                    cardName = nameBuilder.toString().trim();
                } else {
                    cardName = "JCB " + parts[0];
                }
            } else {
                // Extract for non-JCB branded cards (e.g. Seven Card falling back to this
                // format)
                String[] parts = cardInfo.split("　");
                if (parts.length >= 4) {
                    StringBuilder nameBuilder = new StringBuilder();
                    for (int i = 1; i < parts.length - 3; i++) {
                        nameBuilder.append(parts[i]).append(i < parts.length - 4 ? " " : "");
                    }
                    String extracted = nameBuilder.toString().trim();
                    if (!extracted.isEmpty()) {
                        cardName = extracted;
                    }
                }
            }

            if (metadata.cardName == null)
                metadata.cardName = cardName;

            final String finalCardName = cardName;

            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = LocalDate.parse(cols[2], DATE_FORMATTER);
                String storeName = cols[3];

                // Amount might contain commas (e.g. "1,000")
                Integer amount = Integer.valueOf(cols[4].replace(",", ""));
                String memo = cols.length > 10 && !cols[10].isEmpty() ? cols[10] : null; // 摘要 (Summary/Memo)

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
