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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.sony;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.parser.MortgageParser;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;

@Component
public class SonyBankMortgageHtmlParserImpl implements MortgageParser {

    private static final Logger log = LoggerFactory.getLogger(SonyBankMortgageHtmlParserImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");

    @Override
    public boolean canParse(File file) throws IOException {
        Document doc = Jsoup.parse(file, "UTF-8");
        String title = doc.title();
        if (title == null) {
            return false;
        }
        return title.contains("住宅ローン") && title.contains("ソニー銀行")
                && !title.contains("履歴")
                && !doc.select("th:contains(当初借入額)").isEmpty()
                && !doc.select("th:contains(借入残高)").isEmpty();
    }

    @Override
    public MortgageRecord parse(File file) throws IOException, IllegalArgumentException {
        if (!canParse(file)) {
            throw new IllegalArgumentException("Invalid page type for SonyBankMortgageHtmlParserImpl");
        }

        log.info("Parsing Sony Bank Mortgage HTML: {}", file.getName());
        Document doc = Jsoup.parse(file, "UTF-8");

        Map<String, String> dataMap = extractKeyValuePairs(doc);

        String bankName = "ソニー銀行";
        String contractNumber = dataMap.get("契約番号");
        String loanName = dataMap.get("商品名");
        LocalDate startDate = parseDate(dataMap.get("当初借入日"));

        String endDateStr = dataMap.get("最終返済日");
        if (endDateStr == null) {
            endDateStr = dataMap.get("最終返済予定日");
        }
        LocalDate endDate = parseDate(endDateStr);

        Long totalLoanAmount = parseAmount(dataMap.get("当初借入額"));
        Long currentBalance = parseAmount(dataMap.get("借入残高"));

        String rateStr = dataMap.get("適用金利");
        if (rateStr == null) {
            rateStr = dataMap.get("適用金利（年利）");
        }
        Double interestRate = parseRate(rateStr);

        InterestRateType rateType = parseRateType(dataMap.get("金利タイプ"));

        Long monthlyPayment = parseAmount(dataMap.get("次回返済額"));

        Integer loanTermMonths = null;
        if (startDate != null && endDate != null) {
            loanTermMonths = (int) ChronoUnit.MONTHS.between(
                    YearMonth.from(startDate),
                    YearMonth.from(endDate));
        }

        if (totalLoanAmount == null || interestRate == null) {
            throw new IllegalArgumentException(String.format(
                    "Missing mandatory fields for Sony Mortgage: totalLoanAmount=%s, interestRate=%s",
                    totalLoanAmount, interestRate));
        }

        return new MortgageRecord(
                bankName,
                contractNumber,
                loanName,
                totalLoanAmount,
                currentBalance,
                startDate,
                endDate,
                LocalDate.now(), // Balance record date is typically today (date of export)
                loanTermMonths,
                interestRate,
                rateType,
                RepaymentMethod.EQUAL_PAYMENT,
                monthlyPayment,
                Collections.emptyList());
    }

    private Map<String, String> extractKeyValuePairs(Document doc) {
        Map<String, String> map = new HashMap<>();
        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements cells = row.select("th, td");
                if (cells.size() >= 2) {
                    String key = cells.get(0).text().trim();
                    String value = cells.get(1).text().trim();
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private Long parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return null;
        }
        String cleanAmount = amountStr.replace("円", "").replace(",", "").trim();
        try {
            return Long.valueOf(cleanAmount);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", amountStr);
            return null;
        }
    }

    private Double parseRate(String rateStr) {
        if (rateStr == null || rateStr.isBlank()) {
            return null;
        }
        String cleanRate = rateStr.replaceAll("[^0-9.-]", "").trim();
        try {
            return Double.parseDouble(cleanRate) / 100.0;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse rate: {}", rateStr);
            return null;
        }
    }

    private InterestRateType parseRateType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return null;
        }
        if (typeStr.contains("変動")) {
            return InterestRateType.VARIABLE;
        } else if (typeStr.contains("固定")) {
            return InterestRateType.FIXED;
        }
        return null;
    }
}
