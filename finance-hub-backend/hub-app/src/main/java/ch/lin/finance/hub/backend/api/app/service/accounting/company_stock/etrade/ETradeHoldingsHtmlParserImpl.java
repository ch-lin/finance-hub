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
import java.math.BigDecimal;
import java.math.RoundingMode;
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

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeHoldingsParseResult;
import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.BaseStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeCashTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeEsppStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRsuStockLot;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldingsParseResult> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);

    @Override
    public boolean canParse(File file) throws IOException {
        Document doc = Jsoup.parse(file, "UTF-8");
        String title = doc.title();
        if (title == null || !title.contains("E*TRADE - Stock Plan")) {
            return false;
        }

        // Exclude if it's the Benefit History page
        boolean isBenefitHistory = doc.select("h3").stream().anyMatch(e -> e.text().contains("Benefit History"));
        if (isBenefitHistory) {
            return false;
        }

        String bodyText = doc.body().text();
        return bodyText.contains("Total Account Value") || bodyText.contains("Current Account Value");
    }

    @Override
    public ETradeHoldingsParseResult parse(File file, LocalDate recordDate)
            throws IOException, IllegalArgumentException {
        Document doc = Jsoup.parse(file, "UTF-8");

        if (!canParse(file)) {
            throw new IllegalArgumentException("Invalid page type for ETradeHoldingsHtmlParserImpl");
        }

        log.info("Parsing E*TRADE holdings HTML: {}", file.getName());

        String currentSymbol = extractSymbol(doc);

        List<BaseStockLot> lots = new ArrayList<>();
        List<ETradeCashTransaction> cashTransactions = new ArrayList<>();

        String bodyText = doc.body().text();

        // 1. Extract Snapshot
        MonetaryAmount totalAccountValue = extractRequiredCurrencyValue(bodyText, "Total Account Value");
        MonetaryAmount currentAccountValue = extractRequiredCurrencyValue(bodyText, "Current Account Value");
        MonetaryAmount potentialBenefitValue = parseCurrencyValue(
                extractValueAfterLabel(bodyText, "Potential Benefit Value"));

        Double esppPurchasedQty = parseDouble(extractValueAfterLabel(bodyText, "ESPP Purchased Qty."));
        Double rsGrantedQty = parseDouble(extractValueAfterLabel(bodyText, "RS Granted Qty."));

        Double exchangeRate = null;
        Matcher fmtA = Pattern.compile("(JPY|USD)\\s+([0-9]+\\.[0-9]+)").matcher(bodyText);
        if (fmtA.find()) {
            double value = parseDouble(fmtA.group(2));
            String prefix = fmtA.group(1);
            if ("JPY".equals(prefix)) {
                exchangeRate = value;
            } else if (value > 0) {
                exchangeRate = 1.0 / value;
            }
        } else {
            Matcher fmtB = Pattern.compile("@\\s*([0-9\\.]+)\\s*(USD|JPY)").matcher(bodyText);
            if (fmtB.find()) {
                double value = parseDouble(fmtB.group(1));
                String suffix = fmtB.group(2);
                if ("JPY".equals(suffix)) {
                    exchangeRate = value;
                } else if (value > 0) {
                    exchangeRate = 1.0 / value;
                }
            }
        }

        ETradeAccountSnapshot snapshot = new ETradeAccountSnapshot(
                currentSymbol, recordDate, totalAccountValue, currentAccountValue, potentialBenefitValue,
                esppPurchasedQty, rsGrantedQty, exchangeRate);

        // 2. Extract Lots and Cash Transactions
        Elements rows = doc.select("tr");

        ETradeRecordType currentSectionType = null;
        boolean isRsuTrancheSection = false;
        Map<String, Integer> currentHeaderMap = new HashMap<>();

        List<ETradeEsppStockLot> esppLots = new ArrayList<>();
        List<ETradeRsuStockLot> rsuTrancheLots = new ArrayList<>();
        List<ETradeRsuStockLot> rsuSummaryLots = new ArrayList<>();

        LocalDate lastRsuGrantDate = null;
        String lastRsuGrantNumber = null;

        for (Element row : rows) {
            String rowText = row.text();

            // Detect if we entered ESPP or RSU section based on headers
            if (rowText.contains("Purchase Date") && rowText.contains("Purchase Price")
                    && rowText.contains("Purchased Qty.")) {
                currentSectionType = ETradeRecordType.ESPP;
                isRsuTrancheSection = false;
                currentHeaderMap = buildHeaderMap(row);
                continue;
            } else if ((rowText.contains("Grant Number") || rowText.contains("Grant #"))
                    && (rowText.contains("Vest Period") || rowText.contains("Vest Date"))) {
                currentSectionType = ETradeRecordType.RSU;
                isRsuTrancheSection = true;
                currentHeaderMap = buildHeaderMap(row);
                continue;
            } else if (rowText.contains("Grant Date") && rowText.contains("Granted Qty.")
                    && rowText.contains("Unvested Qty.")) {
                currentSectionType = ETradeRecordType.RSU;
                isRsuTrancheSection = false;
                currentHeaderMap = buildHeaderMap(row);
                continue;
            } else if (rowText.contains("Other Holdings")
                    || (rowText.contains("Date") && rowText.contains("Description") && rowText.contains("Value"))) {
                currentSectionType = null;
                isRsuTrancheSection = false;
            }

            Elements cells = row.select("td");
            if (cells.isEmpty()) {
                continue;
            }

            // 2.1 ESPP row
            if (currentSectionType == ETradeRecordType.ESPP && cells.size() >= 5) {
                String dateStr = getCellValue(cells, currentHeaderMap, "Purchase Date");
                LocalDate purchaseDate = parseDate(dateStr);
                if (purchaseDate != null) {
                    MonetaryAmount price = parseCurrencyValue(getCellValue(cells, currentHeaderMap, "Purchase Price"));
                    Double purchasedQty = parseDouble(getCellValue(cells, currentHeaderMap, "Purchased Qty."));
                    Double sellableQty = parseDouble(getCellValue(cells, currentHeaderMap, "Sellable Qty."));
                    MonetaryAmount expectedGainLoss = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Expected Gain/Loss"));
                    MonetaryAmount estMarketValue = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Est. Market Value"));

                    MonetaryAmount estCostBasis = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Est. Cost Basis(per share)", "Est. Cost Basis",
                                    "Cost Basis"));
                    if (estCostBasis == null || estCostBasis.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                        estCostBasis = price;
                    }

                    MonetaryAmount purchaseDateFmv = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Purchase Date FMV"));
                    MonetaryAmount grantDateFmv = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Grant Date FMV"));
                    Double discount = parseDouble(getCellValue(cells, currentHeaderMap, "Discount", "% Discount"));
                    LocalDate grantDate = parseDate(getCellValue(cells, currentHeaderMap, "Grant Date"));

                    MonetaryAmount expectedGainLossPerShare = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Expected Gain/Loss (per share)"));
                    if ((expectedGainLossPerShare == null
                            || expectedGainLossPerShare.getAmount().compareTo(BigDecimal.ZERO) == 0)
                            && sellableQty != null && sellableQty > 0 && expectedGainLoss != null
                            && expectedGainLoss.getAmount().compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal perShare = expectedGainLoss.getAmount().divide(BigDecimal.valueOf(sellableQty), 2,
                                RoundingMode.HALF_UP);
                        expectedGainLossPerShare = MonetaryAmount.of(perShare, expectedGainLoss.getCurrency());
                    }

                    String capitalGainsStatus = getCellValue(cells, currentHeaderMap, "Capital Gains Status");
                    if (capitalGainsStatus == null) {
                        if (rowText.contains("Long Term")) {
                            capitalGainsStatus = "Long Term";
                        } else if (rowText.contains("Short Term")) {
                            capitalGainsStatus = "Short Term";
                        }
                    }

                    String qualifiedPlan = getCellValue(cells, currentHeaderMap, "Qualified Plan?", "Qualified Plan");
                    LocalDate transferableDate = parseDate(getCellValue(cells, currentHeaderMap, "Transferable Date"));

                    ETradeEsppStockLot lot = new ETradeEsppStockLot(
                            currentSymbol, purchaseDate, price, purchasedQty, sellableQty,
                            estCostBasis, purchaseDateFmv, grantDateFmv, discount,
                            grantDate, estMarketValue, expectedGainLoss, expectedGainLossPerShare,
                            capitalGainsStatus, qualifiedPlan, transferableDate);
                    esppLots.add(lot);
                }
            }

            // 2.2 RSU Tranche row (detailed schedule)
            if (currentSectionType == ETradeRecordType.RSU && isRsuTrancheSection && cells.size() >= 4) {
                String grantNumber = getCellValue(cells, currentHeaderMap, "Grant Number", "Grant #", "Grant");
                if (grantNumber != null && !grantNumber.trim().isEmpty()) {
                    lastRsuGrantNumber = grantNumber.trim();
                } else {
                    grantNumber = lastRsuGrantNumber != null ? lastRsuGrantNumber : "UNKNOWN";
                }

                String periodStr = getCellValue(cells, currentHeaderMap, "Vest Period", "Period");
                Integer vestPeriod = parseInteger(periodStr);

                String vestDateStr = getCellValue(cells, currentHeaderMap, "Vest Date");
                LocalDate vestDate = parseDate(vestDateStr);

                if (vestPeriod != null || vestDate != null) {
                    if (vestPeriod == null) {
                        vestPeriod = 1;
                    }
                    if (vestDate == null) {
                        vestDate = lastRsuGrantDate != null ? lastRsuGrantDate : recordDate;
                    }

                    LocalDate grantDate = parseDate(getCellValue(cells, currentHeaderMap, "Grant Date"));
                    if (grantDate == null) {
                        grantDate = lastRsuGrantDate;
                    }

                    LocalDate releaseDate = parseDate(getCellValue(cells, currentHeaderMap, "Release Date"));
                    if (releaseDate == null) {
                        releaseDate = vestDate;
                    }

                    Double sellableQty = parseDouble(
                            getCellValue(cells, currentHeaderMap, "Sellable Qty.", "Sellable Quantity"));
                    Double pendingSaleQty = parseDouble(
                            getCellValue(cells, currentHeaderMap, "Pending Sale Qty.", "Pending Qty."));

                    MonetaryAmount estCostBasis = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Est. Cost Basis(per share)",
                                    "Est. Cost Basis (per share)", "Est. Cost Basis", "Cost Basis"));
                    if (estCostBasis == null || estCostBasis.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                        String costStr = extractValueAfterLabel(rowText, "Est. Cost Basis (per share)");
                        if (costStr == null) {
                            costStr = extractValueAfterLabel(rowText, "Est. Cost Basis");
                        }
                        if (costStr != null) {
                            estCostBasis = parseCurrencyValue(costStr);
                        }
                    }

                    MonetaryAmount estMarketValue = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Est. Market Value", "Market Value"));

                    MonetaryAmount expectedGainLossPerShare = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Expected Gain/Loss (per share)"));
                    if (expectedGainLossPerShare == null
                            || expectedGainLossPerShare.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                        String gainPerShareStr = extractValueAfterLabel(rowText, "Expected Gain/Loss (per share)");
                        if (gainPerShareStr != null) {
                            expectedGainLossPerShare = parseCurrencyValue(gainPerShareStr);
                        }
                    }

                    MonetaryAmount expectedGainLoss = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Expected Gain/Loss"));
                    if ((expectedGainLoss == null || expectedGainLoss.getAmount().compareTo(BigDecimal.ZERO) == 0)
                            && expectedGainLossPerShare != null && sellableQty != null && sellableQty > 0) {
                        BigDecimal totalGain = expectedGainLossPerShare.getAmount()
                                .multiply(BigDecimal.valueOf(sellableQty));
                        expectedGainLoss = MonetaryAmount.of(totalGain, expectedGainLossPerShare.getCurrency());
                    }

                    String capitalGainsStatus = getCellValue(cells, currentHeaderMap, "Capital Gains Status",
                            "Holding Period");
                    if (capitalGainsStatus == null) {
                        if (rowText.contains("Long Term")) {
                            capitalGainsStatus = "Long Term";
                        } else if (rowText.contains("Short Term")) {
                            capitalGainsStatus = "Short Term";
                        }
                    }

                    ETradeRsuStockLot lot = new ETradeRsuStockLot(
                            currentSymbol, grantNumber, grantDate, vestPeriod, vestDate, releaseDate,
                            sellableQty, pendingSaleQty, estCostBasis, estMarketValue,
                            expectedGainLoss, expectedGainLossPerShare, capitalGainsStatus);
                    rsuTrancheLots.add(lot);
                }
            }

            // 2.3 RSU Summary row (top-level Grant summary fallback)
            if (currentSectionType == ETradeRecordType.RSU && !isRsuTrancheSection && cells.size() >= 5) {
                String dateStr = getCellValue(cells, currentHeaderMap, "Grant Date");
                LocalDate date = parseDate(dateStr);
                if (date != null) {
                    lastRsuGrantDate = date;
                    Double sellableQty = parseDouble(getCellValue(cells, currentHeaderMap, "Sellable Qty."));
                    MonetaryAmount estMarketValue = parseCurrencyValue(
                            getCellValue(cells, currentHeaderMap, "Est. Market Value"));

                    String grantNumber = "GRANT-" + date.toString().replace("-", "");
                    lastRsuGrantNumber = grantNumber;

                    ETradeRsuStockLot lot = new ETradeRsuStockLot(
                            currentSymbol, grantNumber, date, 1, date, date,
                            sellableQty, 0.0,
                            MonetaryAmount.of(BigDecimal.ZERO, estMarketValue.getCurrency()),
                            estMarketValue,
                            MonetaryAmount.of(BigDecimal.ZERO, estMarketValue.getCurrency()),
                            MonetaryAmount.of(BigDecimal.ZERO, estMarketValue.getCurrency()),
                            null);
                    rsuSummaryLots.add(lot);
                }
            }

            // 2.4 Cash Transaction row (Other Holdings)
            if (cells.size() >= 3 && currentSectionType == null) {
                String dateStr = cells.get(0).text().trim();
                LocalDate date = parseDate(dateStr);
                if (date != null && !dateStr.contains("Purchase")) {
                    String description = cells.get(1).text().trim();
                    MonetaryAmount amount = parseCurrencyValue(cells.get(2).text());

                    if (!description.isEmpty() && amount.getAmount().compareTo(BigDecimal.ZERO) != 0) {
                        ETradeCashTransaction tx = new ETradeCashTransaction(currentSymbol, date, description, amount);
                        cashTransactions.add(tx);
                    }
                }
            }
        }

        lots.addAll(esppLots);
        if (!rsuTrancheLots.isEmpty()) {
            lots.addAll(rsuTrancheLots);
        } else {
            lots.addAll(rsuSummaryLots);
        }

        return new ETradeHoldingsParseResult(snapshot, lots, cashTransactions);
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
            Matcher m = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE)
                    .matcher(dropdownBtn.text());
            if (m.find()) {
                return m.group(1).toUpperCase(Locale.ROOT);
            }
        }

        Matcher fullDocMatcher = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE)
                .matcher(doc.text());
        if (fullDocMatcher.find()) {
            return fullDocMatcher.group(1).toUpperCase(Locale.ROOT);
        }

        return "UNKNOWN";
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equals("--")) {
            return null;
        }
        String clean = dateStr.trim();
        if (clean.length() == 8 && clean.charAt(2) == '/' && clean.charAt(5) == '/') {
            clean = clean.substring(0, 6) + "20" + clean.substring(6);
        }
        if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
            try {
                return LocalDate.parse(clean, DATE_FORMATTER);
            } catch (Exception e) {
                log.warn("Failed to parse date: {}", dateStr);
            }
        }
        return null;
    }

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty() || val.trim().equals("--")) {
            return null;
        }
        String clean = val.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MonetaryAmount extractRequiredCurrencyValue(String bodyText, String label) {
        String val = extractValueAfterLabel(bodyText, label);
        if (val == null) {
            throw new HtmlParsingException("Missing mandatory label: " + label);
        }
        String clean = val.replaceAll("[^0-9\\.-]", "");
        if (clean.isEmpty() || clean.equals("-") || clean.equals(".")) {
            throw new HtmlParsingException(
                    "Invalid currency value for mandatory label: " + label + ", raw value: " + val);
        }
        return parseCurrencyValue(val);
    }

    private String extractValueAfterLabel(String fullText, String label) {
        int index = fullText.indexOf(label);
        if (index == -1) {
            return null;
        }
        String substring = fullText.substring(index + label.length()).trim();
        if (substring.startsWith(":") || substring.startsWith("-")) {
            substring = substring.substring(1).trim();
        }
        Matcher m = Pattern.compile("^(?:info_outline\\s*)?([¥\\$]?[+-]?[0-9,]+(?:\\.[0-9]{2})?)").matcher(substring);
        if (m.find()) {
            return m.group(1);
        }
        return substring.split("\\s+")[0];
    }

    private MonetaryAmount parseCurrencyValue(String val) {
        if (val == null || val.trim().isEmpty() || val.trim().equals("--")) {
            return MonetaryAmount.of(BigDecimal.ZERO, CurrencyCode.USD);
        }
        String clean = val.replaceAll("[^0-9\\.-]", "");
        if (clean.isEmpty() || clean.equals("-") || clean.equals(".")) {
            return MonetaryAmount.of(BigDecimal.ZERO, CurrencyCode.USD);
        }

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
        if (val == null || val.isEmpty() || val.equals("--")) {
            return 0.0;
        }
        String clean = val.replaceAll("[^0-9\\.-]", "");
        if (clean.isEmpty() || clean.equals("-") || clean.equals(".")) {
            return 0.0;
        }
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
                        return rowCells.get(idx).text().trim();
                    }
                }
            }
        }
        // Second pass: contains
        for (String name : possibleNames) {
            String lowerName = name.toLowerCase();
            boolean nameHasPerShare = lowerName.contains("per share");
            for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                String entryKeyLower = entry.getKey().toLowerCase();
                boolean entryHasPerShare = entryKeyLower.contains("per share");
                if (!nameHasPerShare && entryHasPerShare) {
                    continue;
                }
                if (entryKeyLower.contains(lowerName)) {
                    int idx = entry.getValue();
                    if (idx < rowCells.size()) {
                        return rowCells.get(idx).text().trim();
                    }
                }
            }
        }
        return null;
    }
}
