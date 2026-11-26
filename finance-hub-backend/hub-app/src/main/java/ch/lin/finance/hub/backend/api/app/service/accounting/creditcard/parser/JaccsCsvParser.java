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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Parser implementation for JACCS Card CSV statements.
 * Handles the "ダウンロード日" header and separates metadata parsing from the
 * actual transaction block.
 */
@Component
public class JaccsCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for JACCS transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "JACCS";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // JACCS files typically start with the download date: "ダウンロード日,..."
        return firstLine != null && firstLine.startsWith("ダウンロード日");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean inTransactionSection = false;
        String cardName = "JACCS"; // Default fallback
        metadata.cardName = cardName;

        // Fallback to extract billing month from filename (e.g.,
        // WebDetail202408161016.csv -> 2024-08)
        tryExtractBillingMonthFromFilename(file, metadata, "WebDetail(\\d{4})(\\d{2})");

        while ((line = reader.readLine()) != null) {
            // -1 ensures we don't drop empty trailing columns
            String[] cols = splitCsv(line);

            if (!inTransactionSection) {
                // Priority 1: Extract billing month from the content, highest ㄎ priority (e.g.,
                // "2024年8月お支払分ご利用明細")
                Matcher monthMatcher = Pattern.compile("^(\\d{4})年(\\d{1,2})月").matcher(cols[0].trim());
                if (monthMatcher.find()) {
                    String year = monthMatcher.group(1);
                    String month = monthMatcher.group(2).length() < 2 ? "0" + monthMatcher.group(2)
                            : monthMatcher.group(2);
                    metadata.billingMonth = year + "-" + month;
                    continue;
                }

                // Extract Card Name from the top metadata section (e.g. "カード名,ソフマッププレミアムクラブ")
                if (cols.length >= 2 && "カード名".equals(cols[0])) {
                    cardName = "JACCS " + cols[1].trim();
                    if (metadata.cardName == null)
                        metadata.cardName = cardName;
                    continue;
                }

                // Extract Payment Date (e.g., "お支払予定日,2024/08/27" or the older version
                // "お支払日,...")
                if (cols.length >= 2 && cols[0].contains("お支払い日")) {
                    try {
                        metadata.paymentDate = LocalDate.parse(cols[1].trim(), DATE_FORMATTER);
                    } catch (DateTimeParseException e) {
                        logger.warn("Could not parse payment date from JACCS header: {}", cols[1]);
                    }
                    continue;
                }

                // Extract Total Amount (e.g., "(A)+(B)お支払金額,13563")
                if (cols.length >= 2 && cols[0].contains("お支払金額")) {
                    try {
                        metadata.totalAmount = Integer.valueOf(cols[1].replace(",", "").trim());
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse total amount from JACCS header: {}", cols[1]);
                    }
                    continue;
                }

                // Wait until we hit the header row of the transaction table
                if (cols[0].contains("ご利用年月日")) {
                    inTransactionSection = true;
                }
                continue;
            }

            // We are inside the transaction data block.
            // The list ends when we hit an empty line or a summary line starting with "(A)"
            if (line.trim().isEmpty() || cols[0].startsWith("(")) {
                break;
            }

            // Skip sub-header rows where the first column is empty
            if (cols[0].trim().isEmpty()) {
                continue;
            }

            String dateStr = cols[0].trim();
            if (!isValidTransactionRow(dateStr, "^\\d{4}/\\d{1,2}/\\d{1,2}$", line, file)) {
                continue;
            }

            // Ensure the line has enough columns to represent a transaction
            if (cols.length >= 7) {
                final String finalCardName = cardName;

                tryParseTransaction(line, failedRows, file, () -> {
                    LocalDate transactionDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                    String storeName = cols[1].trim();
                    Integer amount = Integer.valueOf(cols[6].replace(",", "").trim()); // ご利用金額 (Usage Amount)
                    String memo = cols.length > 11 ? cols[11].trim() : null; // 備考 (Remarks)

                    CreditCardTransaction tx = new CreditCardTransaction(
                            finalCardName,
                            transactionDate,
                            storeName,
                            amount);
                    tx.setMemo(memo);

                    transactions.add(tx);
                });
            }
        }
    }
}
