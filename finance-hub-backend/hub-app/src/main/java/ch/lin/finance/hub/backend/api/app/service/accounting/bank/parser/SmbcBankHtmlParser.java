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
 * Parser for Sumitomo Mitsui Banking Corporation (SMBC) HTML statements.
 */
@Component
public class SmbcBankHtmlParser implements BankParser {

    private static final Logger logger = LoggerFactory.getLogger(SmbcBankHtmlParser.class);

    private static final String BANK_NAME = "SMBC";

    // e.g. "2026年6月"
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(\\d{4})年(\\d{1,2})月");

    // e.g. "6月25日"
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");

    @Override
    public boolean canParse(BankFilePreview preview) {
        Document doc = preview.getHtmlDocument();
        if (doc != null) {
            String title = doc.title();
            if (title != null && title.contains("SMBCダイレクト")) {
                return true;
            }
            return !doc.select(".js-meisaiList").isEmpty();
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

        Elements monthSections = doc.select(".js-meisaiMonth");
        for (Element monthSection : monthSections) {
            String monthText = monthSection.selectFirst(".js-dispDateMonth") != null
                    ? monthSection.selectFirst(".js-dispDateMonth").text()
                    : "";

            int year = LocalDate.now().getYear(); // fallback
            Matcher ymMatcher = YEAR_MONTH_PATTERN.matcher(monthText);
            if (ymMatcher.find()) {
                year = Integer.parseInt(ymMatcher.group(1));
            }

            Elements transactions = monthSection.select("dd.detail.js-meisai");
            for (Element txElement : transactions) {
                BankTransaction transaction = parseTransaction(txElement, year);
                if (transaction != null) {
                    result.addTransaction(transaction);
                }
            }
        }

        return result;
    }

    private BankTransaction parseTransaction(Element txElement, int currentYear) {
        try {
            // Description
            Element descEl = txElement.selectFirst(".js-comment");
            String description = descEl != null ? descEl.text().trim() : "Unknown";

            // Date
            Element dateEl = txElement.selectFirst(".js-dispDate");
            LocalDate transactionDate = null;
            if (dateEl != null) {
                String dateText = dateEl.text();
                Matcher mdMatcher = MONTH_DAY_PATTERN.matcher(dateText);
                if (mdMatcher.find()) {
                    int month = Integer.parseInt(mdMatcher.group(1));
                    int day = Integer.parseInt(mdMatcher.group(2));
                    transactionDate = LocalDate.of(currentYear, month, day);
                }
            }
            if (transactionDate == null) {
                logger.warn("Transaction date could not be parsed: {}", dateEl != null ? dateEl.text() : "missing .js-dispDate");
                return null;
            }

            // Amount (Withdrawal or Deposit)
            Integer depositAmount = null;
            Integer withdrawalAmount = null;

            Element minusEl = txElement.selectFirst(".numPrice.-minus.js-amount");
            if (minusEl != null) {
                withdrawalAmount = parseInteger(minusEl.text());
            } else {
                Element plusEl = txElement.selectFirst(".numPrice.-plus.js-amount");
                if (plusEl != null) {
                    depositAmount = parseInteger(plusEl.text());
                }
            }

            // Balance
            Element balanceEl = txElement.selectFirst(".js-torihikigobalance");
            Integer balance = null;
            if (balanceEl != null) {
                balance = parseInteger(balanceEl.text());
            }

            // Memo
            Element memoEl = txElement.selectFirst(".js-meisaiMemoDisp");
            String memo = memoEl != null ? memoEl.text().trim() : null;

            return new BankTransaction(
                    BANK_NAME,
                    transactionDate,
                    description,
                    depositAmount,
                    withdrawalAmount,
                    balance,
                    (memo != null && !memo.isEmpty()) ? memo : null);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            logger.warn("Failed to parse SMBC transaction: {}", txElement.html(), e);
            return null;
        }
    }

    private Integer parseInteger(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String clean = text.replaceAll("[^0-9-]", ""); // keep digits and minus sign
        if (clean.isEmpty() || clean.equals("-")) {
            return null;
        }
        return Integer.valueOf(clean);
    }
}
