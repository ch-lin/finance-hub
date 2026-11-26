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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeBenefitHistoryParseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitEsppPurchase;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuEvent;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuGrant;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuVestSchedule;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ETradeBenefitHistoryHtmlParserImpl implements ETradeParser<ETradeBenefitHistoryParseResult> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);

    @Override
    public boolean canParse(File file) throws IOException {
        Document doc = Jsoup.parse(file, "UTF-8");
        String title = doc.title();
        if (title == null || !title.contains("E*TRADE - Stock Plan")) {
            return false;
        }
        return doc.html().contains("data-analytics-name=\"Benefit History Print\"");
    }

    @Override
    public ETradeBenefitHistoryParseResult parse(File file, LocalDate recordDate)
            throws IOException, IllegalArgumentException {
        Document doc = Jsoup.parse(file, "UTF-8");

        if (!canParse(file)) {
            throw new IllegalArgumentException("Invalid page type for ETradeBenefitHistoryHtmlParserImpl");
        }

        String symbol = extractSymbol(doc);
        log.info("Parsing E*TRADE benefit history HTML for symbol {}: {}", symbol, file.getName());

        // Parse the body

        List<ETradeBenefitRsuGrant> rsuGrants = new ArrayList<>();
        List<ETradeBenefitRsuEvent> rsuEvents = new ArrayList<>();
        List<ETradeBenefitRsuVestSchedule> rsuVestSchedules = new ArrayList<>();
        List<ETradeBenefitEsppPurchase> esppPurchases = new ArrayList<>();

        Elements rows = doc.select("tr");
        int esppDetailsIndex = 0;
        String currentGrantNumber = null;

        // Use separate maps for different headers to avoid state collision
        Map<String, Integer> esppMainMap = null;
        Map<String, Integer> esppDetailsMap = null;
        Map<String, Integer> rsuMainMap = null;
        Map<String, Integer> rsuDetailsMap = null;
        Map<String, Integer> rsuEventsMap = null;
        Map<String, Integer> rsuVestMap = null;
        String currentMode = null; // "ESPP" or "RSU"

        for (Element row : rows) {
            String rowText = row.text();

            // Detect headers
            if ((rowText.contains("Purchase Date") || rowText.contains("購入日"))
                    && (rowText.contains("Purchase Price") || rowText.contains("購入価格"))) {
                currentMode = "ESPP";
                esppMainMap = buildHeaderMap(row);
                continue;
            } else if ((rowText.contains("Grant Date") || rowText.contains("付与日"))
                    && (rowText.contains("Discount") || rowText.contains("割引率"))) {
                esppDetailsMap = buildHeaderMap(row);
                continue;
            } else if ((rowText.contains("Grant Date") || rowText.contains("付与日"))
                    && (rowText.contains("Granted Qty") || rowText.contains("付与数量"))
                    && (rowText.contains("Vested Qty") || rowText.contains("権利確定数量"))
                    && !rowText.contains("Vest Date") && !rowText.contains("権利確定日")) {
                currentMode = "RSU";
                rsuMainMap = buildHeaderMap(row);
                continue;
            } else if ((rowText.contains("Grant Number") || rowText.contains("付与数"))
                    && (rowText.contains("Award Type") || rowText.contains("Type") || rowText.contains("種類"))) {
                rsuDetailsMap = buildHeaderMap(row);
                continue;
            } else if ((rowText.contains("Event Type") || rowText.contains("イベントタイプ"))
                    && (rowText.contains("Action") || rowText.contains("実行"))) {
                rsuEventsMap = buildHeaderMap(row);
                continue;
            } else if ((rowText.contains("Vest Period") || rowText.contains("権利確定期間"))
                    && (rowText.contains("Vest Date") || rowText.contains("権利確定日"))
                    && (rowText.contains("Total Taxes") || rowText.contains("合計支払い税額"))) {
                rsuVestMap = buildHeaderMap(row);
                continue;
            }

            // Extract data based on current section
            Elements tds = row.select("> td");
            if (tds.isEmpty())
                continue;

            if ("ESPP".equals(currentMode)) {
                boolean parsedAsDetails = false;

                // Try ESPP Details first
                if (esppDetailsMap != null) {
                    if (tds.size() == esppDetailsMap.size()) {
                        String gDateStr = getCellValue(tds, esppDetailsMap, "Grant Date", "付与日");
                        if (gDateStr != null) {
                            gDateStr = gDateStr.replace("expand_more", "").replace("expand", "").trim();
                            LocalDate gDate = parseDate(gDateStr);
                            if (gDate != null) {
                                Double discount = parseDouble(
                                        getCellValue(tds, esppDetailsMap, "Discount", "割引率").replace("%", ""));
                                MonetaryAmount gFmv = parseCurrencyValue(
                                        getCellValue(tds, esppDetailsMap, "Grant Date FMV", "付与日 FMV"));
                                MonetaryAmount pFmv = parseCurrencyValue(
                                        getCellValue(tds, esppDetailsMap, "Purchase Date FMV", "購入日 FMV"));
                                String qualifiedText = getCellValue(tds, esppDetailsMap, "Qualified", "適格") != null
                                        ? getCellValue(tds, esppDetailsMap, "Qualified", "適格")
                                        : "";
                                Boolean isQual = qualifiedText.contains("適格") || qualifiedText.contains("Qualified")
                                        || qualifiedText.equalsIgnoreCase("yes")
                                        || qualifiedText.equalsIgnoreCase("はい");
                                LocalDate tDate = parseDate(
                                        getCellValue(tds, esppDetailsMap, "Transferable Date", "譲渡可能日"));

                                if (esppDetailsIndex < esppPurchases.size()) {
                                    ETradeBenefitEsppPurchase target = esppPurchases.get(esppDetailsIndex);
                                    ETradeBenefitEsppPurchase detailed = new ETradeBenefitEsppPurchase(
                                            symbol,
                                            target.getPurchaseDate(),
                                            target.getPurchasePrice(),
                                            target.getPurchasedQty(),
                                            target.getSellableQty(),
                                            target.getEstMarketValue(),
                                            gDate, discount, gFmv, pFmv, isQual, tDate);
                                    esppPurchases.set(esppDetailsIndex, detailed);
                                    esppDetailsIndex++;
                                }
                                parsedAsDetails = true;
                            }
                        }
                    }
                }

                if (!parsedAsDetails && esppMainMap != null) {
                    String pDateStr = getCellValue(tds, esppMainMap, "Purchase Date", "購入日");
                    if (pDateStr != null) {
                        pDateStr = pDateStr.replace("expand_more", "").replace("expand", "").trim();
                        LocalDate pDate = parseDate(pDateStr);
                        if (pDate != null) {
                            MonetaryAmount pPrice = parseCurrencyValue(
                                    getCellValue(tds, esppMainMap, "Purchase Price", "購入価格"));
                            Double pQty = parseDouble(getCellValue(tds, esppMainMap, "Purchased Qty", "購入数量"));
                            Double sQty = parseDouble(getCellValue(tds, esppMainMap, "Sellable Qty", "売却可能数量"));
                            MonetaryAmount mValue = parseCurrencyValue(
                                    getCellValue(tds, esppMainMap, "Est. Market Value", "予想時価", "Market Value"));

                            if (pQty != null && pQty > 0) {
                                esppPurchases.add(new ETradeBenefitEsppPurchase(
                                        symbol, pDate, pPrice, pQty, sQty, mValue, null, null, null, null, null, null));
                            }
                        }
                    }
                }
            } else if ("RSU".equals(currentMode)) {
                boolean parsedAsDetails = false;
                boolean parsedAsEvents = false;
                boolean parsedAsSchedule = false;

                if (rsuDetailsMap != null) {
                    if (tds.size() == rsuDetailsMap.size()) {
                        String awardType = getCellValue(tds, rsuDetailsMap, "Type", "Award Type", "種類", "Award");
                        if (awardType != null && awardType.contains("RSU")) {
                            String rawGrantNum = getCellValue(tds, rsuDetailsMap, "Grant Number", "付与番号", "Grant Num");
                            if (rawGrantNum != null) {
                                currentGrantNumber = rawGrantNum.replace("expand_more", "").replace("expand", "")
                                        .trim();
                                if (!currentGrantNumber.isEmpty()) {
                                    if (!rsuGrants.isEmpty()
                                            && rsuGrants.get(rsuGrants.size() - 1).getGrantNumber()
                                                    .startsWith("TEMP_GRANT_")) {
                                        ETradeBenefitRsuGrant temp = rsuGrants.remove(rsuGrants.size() - 1);
                                        rsuGrants.add(new ETradeBenefitRsuGrant(symbol, currentGrantNumber, temp.getGrantDate(),
                                                temp.getGrantedQty(), temp.getVestedQty(), temp.getSellableQty(),
                                                temp.getEstMarketValue()));
                                    }
                                }
                            }
                            parsedAsDetails = true;
                        }
                    }
                }

                if (!parsedAsDetails && rsuEventsMap != null) {
                    if (tds.size() == rsuEventsMap.size()) {
                        String eventType = getCellValue(tds, rsuEventsMap, "Event Type", "イベントタイプ");
                        LocalDate eDate = parseDate(
                                getCellValue(tds, rsuEventsMap, "Event Date", "Date", "イベント日", "日付"));
                        if (eDate != null && eventType != null && !eventType.isEmpty()
                                && !eventType.contains("- not sorted")) {
                            Double qty = parseDouble(
                                    getCellValue(tds, rsuEventsMap, "Qty", "数量", "Shares", "Quantity"));
                            if (currentGrantNumber != null) {
                                rsuEvents.add(new ETradeBenefitRsuEvent(symbol, currentGrantNumber, eDate, eventType, qty));
                            }
                            parsedAsEvents = true;
                        }
                    }
                }

                if (!parsedAsDetails && !parsedAsEvents && rsuVestMap != null) {
                    if (tds.size() == rsuVestMap.size()) {
                        String vPeriodStr = getCellValue(tds, rsuVestMap, "Vest Period", "権利確定期間");
                        if (vPeriodStr != null && !vPeriodStr.isEmpty()) {
                            try {
                                Integer vPeriod = Integer.valueOf(vPeriodStr.trim());
                                LocalDate vDate = parseDate(getCellValue(tds, rsuVestMap, "Vest Date", "権利確定日"));
                                Double gQty = parseDouble(getCellValue(tds, rsuVestMap, "Granted Qty", "付与数量"));
                                Double vQty = parseDouble(getCellValue(tds, rsuVestMap, "Vested Qty", "権利確定数量"));
                                Double rQty = parseDouble(
                                        getCellValue(tds, rsuVestMap, "Remaining Qty", "残存数量", "Unvested"));
                                Double sQty = parseDouble(getCellValue(tds, rsuVestMap, "Sellable Qty", "売却可能数量"));
                                MonetaryAmount tTaxes = parseCurrencyValue(
                                        getCellValue(tds, rsuVestMap, "Total Taxes", "合計支払い税額", "Taxes"));
                                if (currentGrantNumber != null && vDate != null) {
                                    MonetaryAmount zeroMoney = MonetaryAmount.of(BigDecimal.ZERO, CurrencyCode.USD);
                                    rsuVestSchedules.add(new ETradeBenefitRsuVestSchedule(symbol, currentGrantNumber, vPeriod,
                                            vDate, gQty, vQty, rQty, sQty, zeroMoney, zeroMoney, tTaxes, ""));
                                }
                                parsedAsSchedule = true;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }

                if (!parsedAsDetails && !parsedAsEvents && !parsedAsSchedule && rsuMainMap != null) {
                    if (tds.size() == rsuMainMap.size()) {
                        String gDateStr = getCellValue(tds, rsuMainMap, "Grant Date", "付与日");
                        if (gDateStr != null) {
                            gDateStr = gDateStr.replace("expand_more", "").replace("expand", "").replace("add", "")
                                     .trim();
                            LocalDate gDate = parseDate(gDateStr);
                            if (gDate != null) {
                                Double gQty = parseDouble(getCellValue(tds, rsuMainMap, "Granted Qty", "付与数量"));
                                if (gQty != null && gQty > 0) {
                                    Double vQty = parseDouble(getCellValue(tds, rsuMainMap, "Vested Qty", "権利確定数量"));
                                    Double sQty = parseDouble(getCellValue(tds, rsuMainMap, "Sellable Qty", "売却可能数量"));
                                    MonetaryAmount mValue = parseCurrencyValue(
                                            getCellValue(tds, rsuMainMap, "Est. Market Value", "予想時価", "Market Value"));
                                    String tempName = "TEMP_GRANT_" + gDate.toString();
                                    rsuGrants.add(new ETradeBenefitRsuGrant(symbol, tempName, gDate, gQty, vQty, sQty, mValue));
                                }
                            }
                        }
                    }
                }
            }
        }

        return new ETradeBenefitHistoryParseResult(rsuGrants, rsuEvents, rsuVestSchedules, esppPurchases);
    }

    public String extractSymbol(Document doc) {
        if (doc == null) {
            return "UNKNOWN";
        }
        Element dropdownBtn = doc.selectFirst("[data-test-id='Stockplan Account Drop down'] button.dropdown-toggle");
        if (dropdownBtn == null) {
            dropdownBtn = doc.selectFirst("button#sp-account-dropdown");
        }
        if (dropdownBtn != null) {
            Matcher m = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE).matcher(dropdownBtn.text());
            if (m.find()) {
                return m.group(1).toUpperCase(Locale.ROOT);
            }
        }

        Matcher fullDocMatcher = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE).matcher(doc.text());
        if (fullDocMatcher.find()) {
            return fullDocMatcher.group(1).toUpperCase(Locale.ROOT);
        }

        return "UNKNOWN";
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equals("--")) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private MonetaryAmount parseCurrencyValue(String val) {
        if (val == null || val.trim().isEmpty() || val.trim().equals("--"))
            return MonetaryAmount.of(BigDecimal.ZERO, CurrencyCode.USD);
        String clean = val.replaceAll("[^0-9\\.-]", "");
        if (clean.isEmpty())
            return MonetaryAmount.of(BigDecimal.ZERO, CurrencyCode.USD);

        CurrencyCode currency = CurrencyCode.USD;
        if (val.contains("¥")) {
            currency = CurrencyCode.JPY;
        }

        try {
            return MonetaryAmount.of(new BigDecimal(clean), currency);
        } catch (NumberFormatException e) {
            return MonetaryAmount.of(BigDecimal.ZERO, currency);
        }
    }

    private Double parseDouble(String val) {
        if (val == null || val.isEmpty() || val.equals("--"))
            return 0.0;
        String clean = val.replaceAll("[^0-9\\.-]", "");
        if (clean.isEmpty())
            return 0.0;
        try {
            return Double.valueOf(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Map<String, Integer> buildHeaderMap(Element headerRow) {
        Map<String, Integer> map = new HashMap<>();
        Elements cells = headerRow.select("th, td");
        for (int i = 0; i < cells.size(); i++) {
            String text = cells.get(i).text().replace("expand_more", "").replace("expand", "").trim();
            map.put(text, i);
        }
        return map;
    }

    private String getCellValue(Elements rowCells, Map<String, Integer> headerMap, String... possibleNames) {
        // First pass: exact match (case insensitive)
        for (String name : possibleNames) {
            for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    int idx = entry.getValue();
                    if (idx < rowCells.size()) {
                        return rowCells.get(idx).text();
                    }
                }
            }
        }
        // Second pass: contains
        for (String name : possibleNames) {
            String lowerName = name.toLowerCase();
            for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                if (entry.getKey().toLowerCase().contains(lowerName)) {
                    int idx = entry.getValue();
                    if (idx < rowCells.size()) {
                        return rowCells.get(idx).text();
                    }
                }
            }
        }
        return null;
    }
}
