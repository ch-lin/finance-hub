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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.sompo;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionParser;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionPageType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SompoDcStatusParserImpl implements DefinedContributionParser {

    private static final Pattern SCD_PATTERN = Pattern.compile("(?i)[?&]scd=([a-zA-Z0-9]+)");

    @Override
    public DefinedContributionPageType getPageType() {
        return DefinedContributionPageType.STATUS;
    }

    @Override
    public boolean canParse(Document doc) {
        boolean isSompoPage = doc.title().contains("損保ジャパンＤＣ証券") || doc.title().contains("アンサーネット");
        boolean isAssetStatusPage = doc.text().contains("現在の資産残高") && doc.text().contains("時価単価");
        boolean isNotAllocation = doc.selectFirst("div#pnlKakekinHaibun") == null;
        return isSompoPage && isAssetStatusPage && isNotAllocation;
    }

    @Override
    public LocalDate extractRecordDate(Document doc, LocalDate fallbackDate) {
        Elements dateElements = doc.select("div.headLv2-txt h2");
        for (Element dateElement : dateElements) {
            String text = dateElement.text();
            java.util.regex.Pattern p = java.util.regex.Pattern
                    .compile("(\\d{4})[年/\\-]\\s*(\\d{1,2})[月/\\-]\\s*(\\d{1,2})[日]?");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                try {
                    return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)));
                } catch (NumberFormatException | java.time.DateTimeException ignored) {
                }
            }
        }
        return fallbackDate;
    }

    @Override
    public DefinedContributionAccountSnapshot parse(Document doc, LocalDate recordDate) {
        long totalPrincipal = 0;
        long totalMarketValue = 0;

        Elements tables = doc.select("table");

        // Parse Account Level Data
        for (Element table : tables) {
            String text = table.text();
            if (text.contains("拠出金累計")) {
                Elements rows = table.select("tr"); // Sometimes tbody is missing or nested, we can iterate rows
                for (Element row : rows) {
                    // Only match direct th and td in this row
                    Elements cells = row.select("> th, > td");
                    if (cells.size() == 2 || cells.size() == 3) {
                        String label = cells.get(0).text().trim();
                        if (label.equals("資産残高") && totalMarketValue == 0) {
                            totalMarketValue = parseAmount(cells.get(cells.size() - 1).text());
                        } else if (label.equals("拠出金累計") && totalPrincipal == 0) {
                            totalPrincipal = parseAmount(cells.get(cells.size() - 1).text());
                        }
                    }
                }
            }
        }

        DefinedContributionAccountSnapshot accountSnapshot = new DefinedContributionAccountSnapshot(
                recordDate,
                DefinedContributionType.CORPORATE_DC,
                totalMarketValue,
                totalPrincipal,
                0L, // monthlyEmployerContribution
                0L, // monthlySubscriberContribution
                0L // monthlyTotalContribution
        );

        // Parse Fund Level Data using specific table ID
        Element fundTable = doc.selectFirst("table#grdSyouhinzangaku");
        if (fundTable != null) {
            Elements rows = fundTable.select("> tbody > tr");
            if (rows.isEmpty())
                rows = fundTable.select("> tr"); // Fallback if no tbody

            for (Element row : rows) {
                if (row.hasClass("tableHeader"))
                    continue;

                Elements tds = row.select("> th, > td");
                log.info("Parsing row: classes={}, tds={}, td0='{}', td1='{}'",
                        row.classNames(), tds.size(),
                        tds.isEmpty() ? "" : tds.get(0).text().trim(),
                        tds.size() > 1 ? tds.get(1).text().trim() : "");

                // Data rows usually have 8 columns
                if (tds.size() == 8) {
                    String commodityType = tds.get(0).text().trim();
                    String fundName = tds.get(1).text().trim();
                    String fundCode = extractFundCode(tds.get(1));

                    double currentPrice = parseAmountAsDouble(tds.get(2).text());
                    double units = parseAmountAsDouble(tds.get(3).text());
                    long marketValue = parseAmount(tds.get(4).text());
                    long principalAmount = parseAmount(tds.get(5).text());

                    String returnText = tds.get(6).text().trim();
                    String[] returnParts = returnText.split("\\s+");
                    long returnAmount = parseAmount(returnParts[0]);

                    DefinedContributionFundSnapshot fundSnapshot = new DefinedContributionFundSnapshot(
                            commodityType,
                            fundName,
                            fundCode,
                            units,
                            0.0, // averageCost
                            currentPrice,
                            principalAmount,
                            marketValue,
                            returnAmount);

                    accountSnapshot.addFund(fundSnapshot);
                }
            }
        }

        return accountSnapshot;
    }

    private String extractFundCode(Element cell) {
        if (cell == null) {
            return null;
        }
        Element link = cell.selectFirst("a[href]");
        if (link != null) {
            String href = link.attr("href");
            Matcher m = SCD_PATTERN.matcher(href);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private long parseAmount(String text) {
        String clean = text.replaceAll("[^0-9-]", "");
        if (clean.isEmpty() || clean.equals("-"))
            return 0;
        return Long.parseLong(clean);
    }

    private double parseAmountAsDouble(String text) {
        String clean = text.replaceAll("[^0-9.-]", "");
        if (clean.isEmpty() || clean.equals("-"))
            return 0.0;
        return Double.parseDouble(clean);
    }
}
