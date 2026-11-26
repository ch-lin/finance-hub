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
public class SbiIdecoAllocationParserImpl implements DefinedContributionParser {

    private static final Pattern SCD_PATTERN = Pattern.compile("(?i)[?&]scd=([a-zA-Z0-9]+)");

    @Override
    public DefinedContributionPageType getPageType() {
        return DefinedContributionPageType.ALLOCATION;
    }

    @Override
    public boolean canParse(Document doc) {
        boolean isSbiPage = doc.title().contains("SBI") || doc.text().contains("SBIベネフィット");
        boolean isAllocationPage = doc.selectFirst("div#pnlKakekinHaibun") != null;
        return isSbiPage && isAllocationPage;
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
        long monthlySubscriberContribution = 0L;
        Element kojinContributionEl = doc.selectFirst("span#D_Header1_lblKojinTotalContributionUnit");
        if (kojinContributionEl != null) {
            monthlySubscriberContribution = parseAmount(kojinContributionEl.text());
        }

        DefinedContributionAccountSnapshot accountSnapshot = new DefinedContributionAccountSnapshot(
                recordDate,
                DefinedContributionType.IDECO,
                0L, // totalMarketValue
                0L, // totalPrincipal
                0L, // monthlyEmployerContribution
                monthlySubscriberContribution,
                monthlySubscriberContribution // monthlyTotalContribution
        );

        // Parse Allocation Level Data (掛金の配分割合)
        Element allocationPanel = doc.selectFirst("div#pnlKakekinHaibun");
        if (allocationPanel != null) {
            Element allocationTable = allocationPanel.selectFirst("table.table-style");
            if (allocationTable != null) {
                Elements rows = allocationTable.select("tr.even-row, tr.odd-row");
                for (Element row : rows) {
                    Elements tds = row.select("td");
                    if (tds.size() >= 4) {
                        String commodityType = tds.get(0).text().trim();
                        String fundName = tds.get(1).text().trim();
                        String fundCode = extractFundCode(tds.get(1));
                        double ratio = parseAmountAsDouble(tds.get(2).text());
                        long contribution = parseAmount(tds.get(3).text());

                        DefinedContributionFundSnapshot fundSnapshot = new DefinedContributionFundSnapshot(
                                commodityType,
                                fundName,
                                fundCode,
                                0.0, 0.0, 0.0, 0L, 0L, 0L,
                                contribution, ratio);
                        accountSnapshot.addFund(fundSnapshot);
                    }
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
