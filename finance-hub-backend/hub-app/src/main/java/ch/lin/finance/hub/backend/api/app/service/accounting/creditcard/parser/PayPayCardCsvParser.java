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
 * Parser implementation for PayPay Card CSV statements.
 * Supports multiple encodings (UTF-8 by default based on user feedback) and
 * dynamically resolves column indices.
 */
@Component
public class PayPayCardCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for PayPay Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "PayPay Card";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEncoding(File file) {
        // Per user feedback, PayPay card CSVs are primarily UTF-8.
        return detectEncoding(file, new String[] { "UTF-8", "Windows-31J" }, this::isPayPayHeader, "UTF-8");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        return isPayPayHeader(preview.getHeaderUtf8()) || isPayPayHeader(preview.getHeaderWin31J());
    }

    /**
     * Checks if the header matches the PayPay Card format.
     *
     * @param header The header line to check.
     * @return True if it matches PayPay Card format, false otherwise.
     */
    private boolean isPayPayHeader(String header) {
        // PayPay Card CSV headers start with "利用日/キャンセル日" and "利用店名・商品名"
        return header != null && header.contains("利用日/キャンセル日") && header.contains("利用店名・商品名");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean isFirstLine = true;
        int amountIndex = 4; // Fallback default
        int memoIndex = 3; // Fallback default
        int paymentDateIndex = -1; // Extracted dynamically
        
        if (metadata.cardName == null) {
            metadata.cardName = "PayPay Card";
        }

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsvWithQuotes(line);

            if (isFirstLine) {
                isFirstLine = false;
                // Dynamically find column indices to handle PayPay format changes (e.g., the
                // addition of "決済方法")
                for (int i = 0; i < cols.length; i++) {
                    if ("利用金額".equals(cols[i])) {
                        amountIndex = i;
                    }
                    if ("支払区分".equals(cols[i])) {
                        memoIndex = i;
                    }
                    if ("当月お支払日".equals(cols[i])) {
                        paymentDateIndex = i;
                    }
                }
                continue; // Skip the header row
            }

            if (cols.length <= amountIndex || cols[0].isEmpty()) {
                continue;
            }

            if (!isValidTransactionRow(cols[0], "^\\d{4}/\\d{1,2}/\\d{1,2}$", line, file)) {
                continue;
            }

            // Extract Payment Date
            if (metadata.paymentDate == null && paymentDateIndex != -1 && cols.length > paymentDateIndex
                    && !cols[paymentDateIndex].isEmpty()) {
                try {
                    metadata.paymentDate = LocalDate.parse(cols[paymentDateIndex], DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    logger.warn("Could not parse payment date from PayPay Card row: {}", cols[paymentDateIndex]);
                }
            }

            final int finalAmountIndex = amountIndex;
            final int finalMemoIndex = memoIndex;

            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = LocalDate.parse(cols[0], DATE_FORMATTER);
                String storeName = cols[1];
                Integer amount = Integer.valueOf(cols[finalAmountIndex].replace(",", ""));
                String memo = cols.length > finalMemoIndex ? cols[finalMemoIndex] : null; // Using 支払区分 (Payment
                                                                                          // Category) as memo

                CreditCardTransaction transaction = new CreditCardTransaction(
                        "PayPay Card",
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
