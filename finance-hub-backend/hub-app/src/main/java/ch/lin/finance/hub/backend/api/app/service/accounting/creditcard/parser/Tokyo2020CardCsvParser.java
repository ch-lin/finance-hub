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
 * Parser implementation for Tokyo 2020 Official Card CSV statements.
 * Identifies the file format via specific headers and processes data rows
 * marked with "2".
 */
@Component
public class Tokyo2020CardCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for Tokyo 2020 Official Card transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "Tokyo 2020 Official Card";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEncoding(File file) {
        return detectEncoding(file, new String[] { "UTF-8", "Windows-31J" }, this::isTokyo2020Header, DEFAULT_ENCODING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        return isTokyo2020Header(preview.getHeaderUtf8()) || isTokyo2020Header(preview.getHeaderWin31J());
    }

    /**
     * Checks if the header matches the Tokyo 2020 Official Card format.
     *
     * @param header The header line to check.
     * @return True if it matches the format, false otherwise.
     */
    private boolean isTokyo2020Header(String header) {
        // The header row specifically starts with "1" and contains "お取引日" and "ATM".
        return header != null && header.contains("\"1\"") && header.contains("お取引日") && (header.contains("ATM"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        
        if (metadata.cardName == null) {
            metadata.cardName = "Tokyo 2020 Official Card";
        }

        // Infer billing month from filename (e.g., meisai_20210103090446764.csv ->
        // 2021-01)
        tryExtractBillingMonthFromFilename(file, metadata, "(\\d{4})(\\d{2})\\d*.*\\.csv");

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsvWithQuotes(line);

            // In this format, "1" is the header row, "2" indicates a data row.
            if (cols.length < 5 || !"2".equals(cols[0])) {
                continue;
            }

            if (!isValidTransactionRow(cols[1], "^\\d{4}/\\d{1,2}/\\d{1,2}$", line, file)) {
                continue;
            }

            tryParseTransaction(line, failedRows, file, () -> {
                // Date is in column 1
                LocalDate transactionDate = LocalDate.parse(cols[1], DATE_FORMATTER);
                // Store name is in column 2
                String storeName = cols[2];
                // Amount is in column 4 and might be formatted as a decimal (e.g., "18260.00")
                String amountStr = cols[4].replace(",", "");
                Integer amount = Double.valueOf(amountStr).intValue();

                transactions.add(new CreditCardTransaction(
                        "Tokyo 2020 Official Card",
                        transactionDate,
                        storeName,
                        amount));
            });
        }
    }
}
