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
 * Parser implementation for EPOS Card CSV statements.
 * Identifies EPOS files using the distinctive "種別（ショッピング" header and
 * extracts total amounts from the footer.
 */
@Component
public class EposCsvParser extends AbstractCsvParser {

    /**
     * Date formatter for EPOS transactions.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getParserName() {
        return "EPOS";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canParse(FilePreview preview) {
        String firstLine = preview.getHeaderWin31J();
        // EPOS headers are highly distinct, starting with "種類"
        return firstLine != null && firstLine.startsWith("種別（ショッピング");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parseLines(BufferedReader reader, List<CreditCardTransaction> transactions, List<String> failedRows,
            File file, StatementMetadata metadata) throws Exception {
        String line;
        boolean isFirstLine = true;

        // Infer billing month from filename (e.g. 20210812_UseDetailReference.csv ->
        // 2021-08)
        tryExtractBillingMonthFromFilename(file, metadata, "(\\d{4})(\\d{2})(?:\\d{2})?.*\\.csv");

        if (metadata.cardName == null) {
            File currentDir = file.getParentFile();
            while (currentDir != null) {
                String dirName = currentDir.getName();
                if (dirName.toLowerCase().contains("epos")) {
                    metadata.cardName = dirName;
                    break;
                }
                currentDir = currentDir.getParentFile();
            }
            if (metadata.cardName == null) {
                metadata.cardName = "EPOS Card";
            }
        }

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] cols = splitCsv(line);

            if (isFirstLine) {
                isFirstLine = false;
                continue;
            }

            // Extract total amount from the summary row (e.g., "お支払合計,,,,,4107,")
            if (cols.length >= 6 && cols[0].contains("お支払合計")) {
                try {
                    metadata.totalAmount = Integer.valueOf(cols[5].replace(",", "").trim());
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse total amount from EPOS footer: {}", cols[5]);
                }
                continue;
            }

            // Typical EPOS data has at least 5 columns.
            if (cols.length < 5) {
                continue;
            }
            if (cols[1].trim().isEmpty() || cols[1].trim().contains("ご利用年月日")) {
                continue;
            }

            String dateStr = cols[1].trim();
            if (!isValidTransactionRow(dateStr, "^\\d{4}年\\d{1,2}月\\d{1,2}日$", line, file)) {
                continue;
            }

            tryParseTransaction(line, failedRows, file, () -> {
                LocalDate transactionDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                String storeName = cols[2].trim();
                Integer amount = Integer.valueOf(cols[4].replace(",", "").trim());
                String memo = cols[3].trim(); // ご利用内容 (Usage details)

                CreditCardTransaction transaction = new CreditCardTransaction(
                        "EPOS Card",
                        transactionDate,
                        storeName,
                        amount);
                transaction.setMemo(memo.isEmpty() ? null : memo);
                transactions.add(transaction);
            });
        }
    }
}
