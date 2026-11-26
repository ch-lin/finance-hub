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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Parser implementation for AEON Card CSV statements.
 * Identifies AEON files using the "ご利用カード" header and handles their specific
 * date formats (yyMMdd) and summary metadata rows.
 */
@Component
public class AeonCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for AEON transactions which use the yyMMdd format (e.g.,
     * 230730 for July 30, 2023).
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * Regex pattern to match payment dates like "2023年 9月 4日" or "2024年12月 2日".
     */
    private static final Pattern PAYMENT_DATE_PATTERN = Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "AEON";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // "ご利用カード" is the standard header for AEON CSV files
        return firstLine != null && firstLine.startsWith("ご利用カード");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        String cardName = "AEON Card"; // Fallback default
        LocalDate lastDate = null;
        
        tryExtractBillingMonthFromFilename(file, metadata, "(?i)meisai(\\d{4})(\\d{2})");

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsv(line);

            // Try to parse metadata from the first few lines of the file
            if (cols.length >= 2) {
                String header = cols[0].trim();

                if ("ご利用カード".equals(header)) {
                    cardName = cols[1].trim();
                    if (metadata.cardName == null)
                        metadata.cardName = cardName;
                    continue;
                }
                if (header.contains("ご請求金額")) {
                    try {
                        metadata.totalAmount = Integer.valueOf(cols[1].replace(",", "").trim());
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse total amount from AEON header: {}", cols[1]);
                    }
                    continue;
                }
                if (header.contains("お支払い日") || header.contains("お支払日")) {
                    try {
                        Matcher m = PAYMENT_DATE_PATTERN.matcher(cols[1].trim());
                        if (m.find()) {
                            metadata.paymentDate = LocalDate.of(
                                    Integer.parseInt(m.group(1)),
                                    Integer.parseInt(m.group(2)),
                                    Integer.parseInt(m.group(3)));
                        }
                    } catch (NumberFormatException | DateTimeException e) {
                        logger.warn("Could not parse payment date from AEON header: {}", cols[1]);
                    }
                    continue;
                }
            }

            // AEON data rows require at least 7 columns (Date, User, Store, Method, Empty,
            // Empty, Amount)
            if (cols.length < 7) {
                continue;
            }

            String dateStr = cols[0].trim();
            if (dateStr.isEmpty()) {
                if (lastDate == null) {
                    continue;
                }
                // AEON often leaves the date blank for sub-items like discount adjustments
                // (e.g. 首都高割)
                // We inherit the date from the previous valid transaction.
            } else if (!isValidTransactionRow(dateStr, "^\\d{6}$", line, file)) {
                continue;
            } else {
                lastDate = LocalDate.parse(dateStr, DATE_FORMATTER);
            }

            String storeName = cols[2].trim();
            String amountStr = cols[6].trim();

            if (storeName.isEmpty() || amountStr.isEmpty()) {
                continue;
            }

            // Ignore non-transaction rows (e.g. metadata or summary rows)
            // where the amount column contains text instead of a valid number,
            // but log them as a warning for verification.
            if (!amountStr.matches("^-?[0-9,]+$")) {
                logger.warn("Ignored non-numeric amount row in file {}: {}", file.getName(), line);
                continue;
            }

            final String finalCardName = cardName;
            final LocalDate finalLastDate = lastDate;

            tryParseTransaction(line, failedRows, file, () -> {
                Integer amount = Integer.valueOf(amountStr.replace(",", ""));
                String memo = cols.length > 7 ? cols[7].trim() : null;
                if (memo != null && memo.isEmpty()) {
                    memo = null;
                }

                CreditCardTransaction transaction = new CreditCardTransaction(
                        finalCardName,
                        finalLastDate,
                        storeName,
                        amount);
                transaction.setMemo(memo);
                transactions.add(transaction);
            });
        }
    }
}
