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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.sbi;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;

/**
 * Implementation of {@link SbiNisaParser} using Jsoup to scrape portfolio data.
 */
@Service
public class SbiNisaParserImpl implements SbiNisaParser {

    private static final Logger logger = LoggerFactory.getLogger(SbiNisaParserImpl.class);
    private static final Pattern FUND_CODE_PATTERN = Pattern.compile("/fund/detail/([A-Za-z0-9]+)");

    @Override
    public List<NisaSnapshotRecord> parse(InputStream inputStream, LocalDate recordDate) throws Exception {
        Document document = Jsoup.parse(inputStream, "UTF-8", "");
        List<NisaSnapshotRecord> records = new ArrayList<>();

        // The HTML structure seems to have blocks starting with <h4
        // class="headline-sm">
        // that contain the account type (e.g., NISA (成長)預り).
        // Then an unordered list or divs containing <li class="table-row">

        // Let's iterate over all table rows
        Elements rows = document.select("li.table-row");

        for (Element row : rows) {
            try {
                // Find nearest preceding h4.headline-sm by navigating backwards up the DOM tree
                String accountType = findNearestAccountType(row);
                if (accountType == null || accountType.isBlank()) {
                    // Default fallback if we can't find the header
                    logger.warn(
                            "Could not determine NISA account type header for a row; defaulting to GROWTH. Fund context: {}",
                            row.text());
                    accountType = "Unknown";
                }

                // Verify this is a snapshot row by checking for snapshot-specific data labels
                if (row.selectFirst("[data-label='評価額']") == null && row.selectFirst("[data-label='取得単価']") == null) {
                    continue; // Skip, likely a settings row or empty state
                }

                // Parse Fund Name and Fund Code
                Element fundNameLink = row.selectFirst("a.link.font-bold");
                if (fundNameLink == null) {
                    throw new HtmlParsingException("Fund name link not found in a valid asset snapshot row.");
                }
                String fundName = fundNameLink.text().trim();
                String fundCode = extractFundCode(fundNameLink);

                // Parse Units (保有口数)
                Double units = parseDoubleValue(row, "保有口数 (売却注文中)");

                // Parse Average Cost (取得単価)
                Double averageCost = parseDoubleValue(row, "取得単価");

                // Parse Current Price (基準価額)
                Double currentPrice = parseDoubleValue(row, "基準価額");

                // Parse Total Principal (取得金額)
                Long totalPrincipal = parseLongValue(row, "取得金額");

                // Parse Current Market Value (評価額)
                Long currentMarketValue = parseLongValue(row, "評価額");

                // Parse Unrealized Gain/Loss (評価損益)
                Long unrealizedGainLoss = parseLongValue(row, "評価損益");

                NisaSnapshotRecord record = new NisaSnapshotRecord(
                        recordDate,
                        accountType,
                        fundCode,
                        fundName,
                        units,
                        averageCost,
                        currentPrice,
                        totalPrincipal,
                        currentMarketValue,
                        unrealizedGainLoss);
                records.add(record);

                logger.debug("Parsed NISA record: {} - {} [{}] (Units: {}, Value: {})",
                        accountType, fundName, fundCode, units, currentMarketValue);

            } catch (HtmlParsingException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new HtmlParsingException("Failed to parse a row in NISA HTML: " + e.getMessage(), e);
            }
        }

        return records;
    }

    @Override
    public List<NisaContributionSetting> parseSettings(InputStream inputStream, LocalDate recordDate) throws Exception {
        Document document = Jsoup.parse(inputStream, "UTF-8", "");
        List<NisaContributionSetting> settings = new ArrayList<>();

        Elements rows = document.select("li.table-row");
        for (Element row : rows) {
            try {
                Element fundNameEl = row.selectFirst("div[data-testid='fundName'] p");
                if (fundNameEl == null) {
                    fundNameEl = row.selectFirst("div[data-testid='fundName']");
                }
                if (fundNameEl == null)
                    continue;
                String fundName = fundNameEl.text().trim();

                Element linkEl = row.selectFirst("div[data-testid='fundName'] a");
                if (linkEl == null) {
                    linkEl = row.selectFirst("a[data-testid='linkUrlPC']");
                }
                String fundCode = extractFundCode(linkEl);

                Element accountTypeEl = row.selectFirst("[data-testid='accountType_PC']");
                String accountType = accountTypeEl != null ? accountTypeEl.text().trim() : "Unknown";

                Element courseEl = row.selectFirst("[data-testid='courseType_PC']");
                if (courseEl == null) {
                    courseEl = row.selectFirst("div[data-testid='courseType'] p");
                }
                String course = courseEl != null ? courseEl.text().trim() : "Unknown";

                Element amountEl = row.selectFirst("[data-testid='settingAmount_PC']");
                if (amountEl == null) {
                    amountEl = row.selectFirst("div[data-testid='settingAmount']");
                }
                long settingAmount = extractLong(amountEl != null ? amountEl.text() : "0");

                NisaContributionSetting setting = new NisaContributionSetting(
                        recordDate,
                        accountType,
                        fundCode,
                        fundName,
                        course,
                        settingAmount);
                settings.add(setting);

                logger.debug("Parsed NISA setting: {} [{}] - {} - {}",
                        fundName, fundCode, course, settingAmount);

            } catch (HtmlParsingException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new HtmlParsingException("Failed to parse a row in NISA Setting HTML: " + e.getMessage(), e);
            }
        }
        return settings;
    }

    private String extractFundCode(Element linkElement) {
        if (linkElement == null) {
            return null;
        }
        String href = linkElement.attr("href");
        if (href != null && !href.isBlank()) {
            Matcher matcher = FUND_CODE_PATTERN.matcher(href);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String findNearestAccountType(Element row) {
        // Go up to the parent block and look for the previous sibling h4
        Element current = row;
        while (current != null) {
            // Check previous siblings
            Element sibling = current.previousElementSibling();
            while (sibling != null) {
                if (sibling.tagName().equals("h4") && sibling.hasClass("headline-sm")) {
                    return sibling.text().trim();
                }
                // Also check if the sibling contains the h4
                Element innerH4 = sibling.selectFirst("h4.headline-sm");
                if (innerH4 != null) {
                    return innerH4.text().trim();
                }
                sibling = sibling.previousElementSibling();
            }
            current = current.parent();
        }
        return null;
    }

    private Double parseDoubleValue(Element row, String dataLabel) {
        Element item = row.selectFirst("div[data-label='" + dataLabel + "']");
        if (item == null) {
            // Also try searching for any element with this data-label
            item = row.selectFirst("[data-label='" + dataLabel + "']");
        }
        if (item != null) {
            // Sometimes it has multiple <p> tags, the first one is usually the main value
            Element p = item.selectFirst("p");
            String text = p != null ? p.text() : item.text();
            return extractDouble(text);
        }
        return 0.0;
    }

    private Long parseLongValue(Element row, String dataLabel) {
        Element item = row.selectFirst("div[data-label='" + dataLabel + "']");
        if (item == null) {
            item = row.selectFirst("[data-label='" + dataLabel + "']");
        }
        if (item != null) {
            Element p = item.selectFirst("p");
            String text = p != null ? p.text() : item.text();
            return extractLong(text);
        }
        return 0L;
    }

    private Double extractDouble(String text) {
        if (text == null || text.isBlank())
            return 0.0;
        // Remove commas, currency symbols, and units (e.g., "924,141口" -> "924141",
        // "+1,234.5円" -> "1234.5")
        String cleaned = text.replaceAll("[^\\d.\\-+]", "");
        try {
            return Double.valueOf(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Long extractLong(String text) {
        if (text == null || text.isBlank())
            return 0L;
        String cleaned = text.replaceAll("[^\\d\\-+]", "");
        try {
            return Long.valueOf(cleaned);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
