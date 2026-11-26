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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.sbi;

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

@Component
public class SbiIdecoStatusParserImpl implements DefinedContributionParser {

    private static final Pattern SCD_PATTERN = Pattern.compile("(?i)[?&]scd=([a-zA-Z0-9]+)");

    @Override
    public DefinedContributionPageType getPageType() {
        return DefinedContributionPageType.STATUS;
    }

    @Override
    public boolean canParse(Document doc) {
        boolean isSbiPage = doc.title().contains("SBI") || doc.text().contains("SBIベネフィット");
        boolean isAssetStatusPage = doc.text().contains("資産残高") && doc.text().contains("残高数量");
        boolean isNotAllocation = doc.selectFirst("div#pnlKakekinHaibun") == null;
        return isSbiPage && isAssetStatusPage && isNotAllocation;
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
        // Parse Account Level Data
        long totalPrincipal = 0;
        long totalMarketValue = 0;

        Elements ths = doc.select("th");
        for (Element th : ths) {
            if (th.text().equals("資産残高")) {
                Element nextTd = th.nextElementSibling();
                if (nextTd != null && nextTd.tagName().equals("td")) {
                    String val = nextTd.text().replaceAll("[^0-9-]", "");
                    if (!val.isEmpty()) {
                        totalMarketValue = Long.parseLong(val);
                    }
                }
            } else if (th.text().equals("拠出金累計")) {
                Element nextTd = th.nextElementSibling();
                if (nextTd != null && nextTd.tagName().equals("td")) {
                    String val = nextTd.text().replaceAll("[^0-9-]", "");
                    if (!val.isEmpty()) {
                        totalPrincipal = Long.parseLong(val);
                    }
                }
            }
        }

        DefinedContributionAccountSnapshot accountSnapshot = new DefinedContributionAccountSnapshot(
                recordDate,
                DefinedContributionType.IDECO,
                totalMarketValue,
                totalPrincipal,
                0L, // monthlyEmployerContribution
                0L, // monthlySubscriberContribution
                0L // monthlyTotalContribution
        );

        // Parse Fund Level Data
        Elements tables = doc.select("table");
        for (Element table : tables) {
            if (table.text().contains("残高数量")) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Elements tds = row.select("> th, > td");
                    // We need rows with 8 columns (matching the headers we saw)
                    if (tds.size() >= 8) {
                        String commodityType = tds.get(0).text().trim();
                        if (commodityType.contains("商品タイプ") || commodityType.length() > 50) {
                            continue; // Skip header row or invalid rows
                        }
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
                // Break after finding the first valid fund table to avoid duplicates
                // (responsive design)
                if (!accountSnapshot.getFunds().isEmpty()) {
                    break;
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
