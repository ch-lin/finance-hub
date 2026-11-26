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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;

/**
 * Implementation of {@link SalaryParser} using Jsoup to scrape salary statement
 * details from HTML.
 */
@Service
public class SalaryParserImpl implements SalaryParser {

    private static final Logger logger = LoggerFactory.getLogger(SalaryParserImpl.class);
    private static final Pattern TITLE_PATTERN = Pattern.compile("(\\d{4})年(\\d{1,2})月");

    @Override
    public SalaryRecord parse(InputStream inputStream, String filename) {
        try {
            Document doc = Jsoup.parse(inputStream, "UTF-8", "");

            // 1. Extract Date from h1 or filename
            LocalDate paymentDate = extractDate(doc, filename);
            SalaryRecord record = new SalaryRecord(paymentDate);

            // 2. Extract tables
            Elements tables = doc.select("table");
            Map<String, Long> details = new HashMap<>();

            for (Element table : tables) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Element th = row.selectFirst("th");
                    Element td = row.selectFirst("td.text-right");

                    if (th != null && td != null) {
                        String key = th.text().trim();
                        String valueStr = td.text().trim().replace(",", "").replace("&nbsp;", "").replace("¥", "");

                        if (!key.isEmpty() && !valueStr.isEmpty()) {
                            try {
                                long value = Long.parseLong(valueStr);
                                details.put(key, value);
                                mapCoreField(record, key, value);
                            } catch (NumberFormatException e) {
                                // Ignore non-numeric rows
                            }
                        }
                    }
                }
            }

            // Extract 総支給 (Gross) and 差引支給額 (Net) from footer
            for (Element table : tables) {
                Elements ths = table.select("tfoot tr th");
                Elements tds = table.select("tfoot tr td.text-right");
                for (int i = 0; i < ths.size(); i++) {
                    if (i < tds.size()) {
                        String key = ths.get(i).text().trim();
                        String valStr = tds.get(i).text().trim().replace(",", "").replace("&nbsp;", "").replace("¥",
                                "");
                        if ((key.equals("総支給") || key.equals("支給額合計")) && !valStr.isEmpty()) {
                            try {
                                long value = Long.parseLong(valStr);
                                record.setGrossAmount(value);
                                details.put(key, value);
                            } catch (NumberFormatException e) {
                            }
                        }
                        if (key.equals("差引支給額") && !valStr.isEmpty()) {
                            try {
                                long value = Long.parseLong(valStr);
                                record.setNetAmount(value);
                                details.put(key, value);
                            } catch (NumberFormatException e) {
                            }
                        }
                    }
                }
            }

            // 3. Extract Notes (備考/記事)
            StringBuilder noteBuilder = new StringBuilder();
            Element noteTable = doc.getElementById("listTable_S6N002_F");
            if (noteTable != null) {
                Elements noteTds = noteTable.select("td");
                for (Element td : noteTds) {
                    String text = td.text().trim();
                    if (!text.isEmpty()) {
                        noteBuilder.append(text).append("\n");
                    }
                }
            }

            if (noteBuilder.length() > 0) {
                record.setNote(noteBuilder.toString().trim());
            }

            record.setDetails(details);
            return record;

        } catch (IOException e) {
            logger.error("Failed to parse salary statement from file: " + filename, e);
            throw new RuntimeException("Failed to parse salary statement", e);
        }
    }

    private LocalDate extractDate(Document doc, String filename) {
        // The file usually contains text like "20xx年xx月給与Process 給与明細書"
        // We scan the document's entire visible text for the YYYY年MM月 pattern.
        Matcher matcher = TITLE_PATTERN.matcher(doc.text());
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            logger.info("Extracted date {}/{} from document", year, month);
            return LocalDate.of(year, month, 25); // Assume 25th as standard payday
        }

        // If not found in text, try filename as a last resort
        Matcher filenameMatcher = TITLE_PATTERN.matcher(filename);
        if (filenameMatcher.find()) {
            int year = Integer.parseInt(filenameMatcher.group(1));
            int month = Integer.parseInt(filenameMatcher.group(2));
            return LocalDate.of(year, month, 25);
        }

        logger.warn("Could not find YYYY年MM月 in document body or filename. Using current date as ultimate fallback.");
        return LocalDate.now().withDayOfMonth(25);
    }

    private void mapCoreField(SalaryRecord record, String key, long value) {
        switch (key) {
            case "基本給" -> record.setBaseSalary(value);
            case "残業手当" -> record.setOvertimePay(value);
            case "コミッション" -> record.setCommission(value);
            case "ﾌｨｯﾄﾈｽ補助", "フィットネス補助" -> record.setFitnessAllowance(value);
            case "健康保険料" -> record.setHealthInsurance(value);
            case "厚生年金保険料" -> record.setPension(value);
            case "雇用保険料" -> record.setEmploymentInsurance(value);
            case "所得税" -> record.setIncomeTax(value);
            case "住民税" -> record.setResidentTax(value);
            case "持株会控除", "持株会控除(給与)" -> record.setEsppContribution(value);
            case "総支給", "支給額合計" -> record.setGrossAmount(value);
            case "差引支給額" -> record.setNetAmount(value);
        }
    }
}
