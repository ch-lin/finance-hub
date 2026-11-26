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
package ch.lin.finance.hub.backend.api.app.service.accounting.bank.parser;


import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.BankFilePreview;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.BankParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.BankParser;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;

/**
 * Parser for Mitsubishi UFJ (MUFG) Bank HTML statements.
 */
@Component
public class MufgBankHtmlParser implements BankParser {

    private static final Logger logger = LoggerFactory.getLogger(MufgBankHtmlParser.class);

    private static final String BANK_NAME = "MUFG";

    // e.g. "2026年"
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})年");

    // e.g. "1/13"
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})");

    @Override
    public boolean canParse(BankFilePreview preview) {
        Document doc = preview.getHtmlDocument();
        if (doc != null) {
            String title = doc.title();
            if (title != null && title.contains("三菱ＵＦＪ")) {
                return true;
            }
            return !doc.select(".add-tbl-01").isEmpty();
        }
        return false;
    }

    @Override
    public BankParseResult parse(BankFilePreview preview) throws Exception {
        BankParseResult result = new BankParseResult();
        Document doc = preview.getHtmlDocument();
        if (doc == null) {
            throw new IllegalArgumentException("Cannot parse non-HTML document");
        }

        Elements rows = doc.select(".add-tbl-01 tbody tr");
        int currentYear = LocalDate.now().getYear();

        for (Element row : rows) {
            Elements tds = row.select("td");

            // Check if it's a year header row (e.g., <td colspan="6">2026年</td>)
            if (tds.size() == 1 && row.hasClass("noline")) {
                String yearText = tds.get(0).text();
                Matcher yearMatcher = YEAR_PATTERN.matcher(yearText);
                if (yearMatcher.find()) {
                    currentYear = Integer.parseInt(yearMatcher.group(1));
                }
                continue;
            }

            // Normal transaction row expects at least 6 columns
            if (tds.size() >= 6) {
                BankTransaction transaction = parseTransactionRow(tds, currentYear);
                if (transaction != null) {
                    result.addTransaction(transaction);
                }
            }
        }

        return result;
    }

    private BankTransaction parseTransactionRow(Elements tds, int year) {
        try {
            // 0: Date
            String dateText = tds.get(0).text().trim();
            LocalDate transactionDate = null;
            Matcher mdMatcher = MONTH_DAY_PATTERN.matcher(dateText);
            if (mdMatcher.find()) {
                int month = Integer.parseInt(mdMatcher.group(1));
                int day = Integer.parseInt(mdMatcher.group(2));
                transactionDate = LocalDate.of(year, month, day);
            }
            if (transactionDate == null) {
                return null; // Date is required, maybe an empty row
            }

            // 1: Withdrawal
            Integer withdrawalAmount = parseInteger(tds.get(1).text());

            // 2: Deposit
            Integer depositAmount = parseInteger(tds.get(2).text());

            // 3: Description
            String description = tds.get(3).text().trim();
            if (description.isEmpty()) {
                description = "Unknown";
            }

            // 4: Balance
            Integer balance = parseInteger(tds.get(4).text());

            // 5: Memo (Input field, text might be in an input value or span)
            String memo;
            Element memoInput = tds.get(5).selectFirst("input[type=text]");
            if (memoInput != null) {
                memo = memoInput.val().trim();
            } else {
                memo = tds.get(5).text().replace("メモ編集", "").trim();
            }

            return new BankTransaction(
                    BANK_NAME,
                    transactionDate,
                    description,
                    depositAmount,
                    withdrawalAmount,
                    balance,
                    (memo != null && !memo.isEmpty()) ? memo : null);
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            logger.warn("Failed to parse MUFG row: {}", tds.html(), e);
            return null;
        }
    }

    private Integer parseInteger(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String clean = text.replaceAll("[^0-9]", ""); // only keep digits
        if (clean.isEmpty()) {
            return null;
        }
        return Integer.valueOf(clean);
    }
}
