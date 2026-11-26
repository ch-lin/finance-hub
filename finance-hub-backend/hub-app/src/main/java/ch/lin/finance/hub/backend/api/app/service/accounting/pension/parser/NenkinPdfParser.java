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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NenkinPdfParser {

    private static final String DATE_REGEX =
            "(?:(令[\\s　]*和|平[\\s　]*成|昭[\\s　]*和)[\\s　]*([0-9\\s　]*[0-9]|元)|([0-9]{4}))[\\s　]*(?:年|\\s|　|\\.|/)[\\s　]*([0-9\\s　]*[0-9])[\\s　]*(?:月|\\s|　|\\.|/)[\\s　]*([0-9\\s　]*[0-9])(?:[\\s　]*日)?";

    private static final Pattern DOWNLOAD_DATE_PATTERN = Pattern.compile(
            "ダウンロード日[\\s\\S]{0,100}?" + DATE_REGEX);

    private static final Pattern CURRENT_RECORD_AS_OF_AFTER_PATTERN = Pattern.compile(
            "現在の加入記録[\\s\\S]{0,500}?" + DATE_REGEX);

    private static final Pattern CURRENT_RECORD_AS_OF_BEFORE_PATTERN = Pattern.compile(
            DATE_REGEX + "[\\s\\S]{0,50}?現在の加入記録");

    private static final Pattern DATA_CREATION_DATE_PATTERN = Pattern.compile(
            "(?:【[\\s　]*デ[\\s　]*ー?[\\s　]*タ?[\\s　]*作?[\\s　]*成?[\\s　]*日[\\s　]*】|データ作成日)[\\s\\S]{0,400}?" + DATE_REGEX);

    public PensionRecord parse(File pdfFile) throws IOException {
        String text;
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        }

        LocalDate recordDate = extractRecordDate(text, pdfFile);

        Long basicPensionAmount = extractLong(text, Pattern.compile("老齢基礎年金\\s*([0-9,]+)"));
        
        java.util.List<Long> ippanKouseiMatches = extractAllLongs(text, Pattern.compile("一般厚生年金期間\\s*([0-9,]+)(?:\\s*円)?"));
        Long kouseiPremium = !ippanKouseiMatches.isEmpty() ? ippanKouseiMatches.get(0) : null;
        Long kouseiPensionAmount = ippanKouseiMatches.size() > 1 ? ippanKouseiMatches.get(1) : null;

        java.util.List<Long> totalMatches = extractAllLongs(text, Pattern.compile("（１）と（２）の合計\\s*([0-9,]+)(?:\\s*円)?"));
        Long totalPensionAmount = totalMatches.size() > 1 ? totalMatches.get(1) : null;

        if (totalPensionAmount == null && basicPensionAmount != null && kouseiPensionAmount != null) {
            totalPensionAmount = basicPensionAmount + kouseiPensionAmount;
        }

        Long kokuminPremium = extractLong(text, Pattern.compile("（１）国民年金保険料[\\s\\S]{0,50}?([0-9,]+)(?:\\s*円)?"));
        if (kokuminPremium == null) {
            kokuminPremium = extractLong(text, Pattern.compile("国民年金保険料\\s*([0-9,]+)(?:\\s*円)?"));
        }

        Long totalPremiumPaid = !totalMatches.isEmpty() ? totalMatches.get(0) : null;
        if (totalPremiumPaid == null && kouseiPremium != null) {
            totalPremiumPaid = kouseiPremium + (kokuminPremium != null ? kokuminPremium : 0L);
        }

        EnrolledMonths months = extractEnrolledMonths(text);

        return new PensionRecord(
                recordDate,
                basicPensionAmount,
                kouseiPensionAmount,
                totalPensionAmount,
                months.kokuminCategory1Months(),
                months.kokuminCategory3Months(),
                months.kokuminTotalMonths(),
                months.kouseiGeneralMonths(),
                months.kouseiTotalMonths(),
                months.totalEnrolledMonths(),
                months.qualifyingMonths(),
                kokuminPremium,
                kouseiPremium,
                totalPremiumPaid
        );
    }

    private Long extractLong(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Long.valueOf(matcher.group(1).replace(",", "").trim());
            } catch (NumberFormatException e) {
                log.warn("Failed to parse long value from matched group: {}", matcher.group(1));
            }
        }
        return null;
    }

    private java.util.List<Long> extractAllLongs(String text, Pattern pattern) {
        java.util.List<Long> results = new java.util.ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            try {
                results.add(Long.valueOf(matcher.group(1).replace(",", "").trim()));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse long value from matched group: {}", matcher.group(1));
            }
        }
        return results;
    }

    LocalDate extractRecordDate(String text, File pdfFile) {
        if (text != null && !text.isBlank()) {
            LocalDate date = extractPatternDate(text, DATA_CREATION_DATE_PATTERN);
            if (date != null) {
                log.info("Extracted pension record date from データ作成日: {}", date);
                return date;
            }

            date = extractPatternDate(text, CURRENT_RECORD_AS_OF_AFTER_PATTERN);
            if (date != null) {
                log.info("Extracted pension record date from 現在の加入記録 (after pattern): {}", date);
                return date;
            }

            date = extractPatternDate(text, CURRENT_RECORD_AS_OF_BEFORE_PATTERN);
            if (date != null) {
                log.info("Extracted pension record date from 現在の加入記録 (before pattern): {}", date);
                return date;
            }

            date = extractPatternDate(text, DOWNLOAD_DATE_PATTERN);
            if (date != null) {
                log.info("Extracted pension record date from ダウンロード日: {}", date);
                return date;
            }
        }

        if (pdfFile != null && pdfFile.exists()) {
            try {
                FileTime modifiedTime = Files.getLastModifiedTime(pdfFile.toPath());
                LocalDate fallback = modifiedTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                log.info("Fallback pension record date to file last modified time: {}", fallback);
                return fallback;
            } catch (IOException e) {
                log.warn("Failed to read last modified time from file {}: {}", pdfFile.getName(), e.getMessage());
            }
        }

        LocalDate fallbackNow = LocalDate.now();
        log.warn("Falling back to LocalDate.now() for pension record date: {}", fallbackNow);
        return fallbackNow;
    }

    private LocalDate extractPatternDate(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return parseDate(matcher);
        }
        return null;
    }

    private LocalDate parseDate(Matcher matcher) {
        String era = matcher.group(1);
        String eraYearStr = matcher.group(2);
        String westernYearStr = matcher.group(3);
        String monthStr = matcher.group(4);
        String dayStr = matcher.group(5);

        try {
            int year;
            if (era != null && eraYearStr != null) {
                String cleanEra = era.replaceAll("[\\s　]+", "");
                String trimmed = eraYearStr.replaceAll("[\\s　]+", "");
                if (trimmed.isEmpty()) {
                    return null;
                }
                int eraYear = "元".equals(trimmed) ? 1 : Integer.parseInt(trimmed);
                if ("令和".equals(cleanEra)) {
                    year = 2018 + eraYear;
                } else if ("平成".equals(cleanEra)) {
                    year = 1988 + eraYear;
                } else if ("昭和".equals(cleanEra)) {
                    year = 1925 + eraYear;
                } else {
                    return null;
                }
            } else if (westernYearStr != null) {
                String cleanWesternYear = westernYearStr.replaceAll("[\\s　]+", "");
                if (cleanWesternYear.isEmpty()) {
                    return null;
                }
                year = Integer.parseInt(cleanWesternYear);
            } else {
                return null;
            }

            if (monthStr == null || dayStr == null) {
                return null;
            }
            String cleanMonth = monthStr.replaceAll("[\\s　]+", "");
            String cleanDay = dayStr.replaceAll("[\\s　]+", "");
            if (cleanMonth.isEmpty() || cleanDay.isEmpty()) {
                return null;
            }

            int month = Integer.parseInt(cleanMonth);
            int day = Integer.parseInt(cleanDay);

            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date from matched text: {}", matcher.group(0), e);
        }
        return null;
    }

    public record EnrolledMonths(
            Integer kokuminCategory1Months,
            Integer kokuminCategory3Months,
            Integer kokuminTotalMonths,
            Integer kouseiGeneralMonths,
            Integer kouseiTotalMonths,
            Integer totalEnrolledMonths,
            Integer qualifyingMonths
    ) {}

    public EnrolledMonths extractEnrolledMonths(String text) {
        if (text == null || text.isBlank()) {
            return new EnrolledMonths(null, null, null, null, null, null, null);
        }

        // 1. Try Teikibin Table Structure (ねんきん定期便: これまでの年金加入期間)
        EnrolledMonths teikibinMonths = extractTeikibinTableMonths(text);
        if (teikibinMonths != null) {
            return teikibinMonths;
        }

        // 2. Try Electronic Inquiry Form (被保険者記録照会回答票)
        EnrolledMonths inquiryMonths = extractInquiryFormMonths(text);
        if (inquiryMonths != null) {
            return inquiryMonths;
        }

        // 3. Fallback to regex with optional "月" suffix (mock data or inline text)
        EnrolledMonths regexMonths = extractRegexMonths(text);
        if (regexMonths != null) {
            return regexMonths;
        }

        return new EnrolledMonths(null, null, null, null, null, null, null);
    }

    private EnrolledMonths extractTeikibinTableMonths(String text) {
        if (!text.contains("これまでの年金加入期間")) {
            return null;
        }

        List<List<Integer>> candidateRows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^(?:[0-9]+\\s+)+[0-9]+$")) {
                String[] parts = trimmed.split("\\s+");
                List<Integer> row = new ArrayList<>();
                boolean valid = true;
                for (String p : parts) {
                    try {
                        int val = Integer.parseInt(p);
                        if (val > 600) { // max 600 months (50 years)
                            valid = false;
                            break;
                        }
                        row.add(val);
                    } catch (NumberFormatException e) {
                        valid = false;
                        break;
                    }
                }
                if (valid && (row.size() >= 3 && row.size() <= 4)) {
                    candidateRows.add(row);
                }
            }
        }

        for (List<Integer> kRow : candidateRows) {
            if (kRow.size() != 4) {
                continue;
            }
            int kCat1 = kRow.get(0);
            int kCat3 = kRow.get(1);
            int kOther = kRow.get(2);
            int kTotal = kRow.get(3);
            if (kCat1 + kCat3 + kOther > kTotal && kTotal != 0) {
                continue;
            }

            for (List<Integer> hRow : candidateRows) {
                if (hRow == kRow) {
                    continue;
                }
                int hGen = hRow.get(0);
                int hKoumu = hRow.get(1);
                int hShigaku = hRow.get(2);
                int hTotal = hRow.size() >= 4 ? hRow.get(3) : (hGen + hKoumu + hShigaku);

                for (List<Integer> tRow : candidateRows) {
                    if (tRow == kRow || tRow == hRow || tRow.size() != 3) {
                        continue;
                    }
                    int totalEnrolled = tRow.get(0);
                    int tOther = tRow.get(1);
                    int qualifying = tRow.get(2);

                    if (totalEnrolled == kTotal + hTotal && qualifying == totalEnrolled + tOther) {
                        return new EnrolledMonths(
                                kCat1,
                                kCat3,
                                kTotal,
                                hGen,
                                hTotal,
                                totalEnrolled,
                                qualifying
                        );
                    }
                }
            }
        }

        return null;
    }

    private EnrolledMonths extractInquiryFormMonths(String text) {
        if (!text.contains("被保険者記録照会回答票")) {
            return null;
        }

        Matcher m = Pattern.compile("([0-9]+)\\s+([0-9]+)\\s+([0-9]+)\\s+([0-9]+)\\s*\\(\\s*([0-9]+)\\s*\\)\\s*\\(\\s*([0-9]+)\\s*\\)\\s*([0-9]+)").matcher(text);
        if (m.find()) {
            int kouseiGeneral = Integer.parseInt(m.group(1));
            int totalEnrolled = Integer.parseInt(m.group(7));
            int kokuminTotal = Math.max(0, totalEnrolled - kouseiGeneral);
            return new EnrolledMonths(
                    0,
                    0,
                    kokuminTotal,
                    kouseiGeneral,
                    kouseiGeneral,
                    totalEnrolled,
                    totalEnrolled
            );
        }
        return null;
    }

    private EnrolledMonths extractRegexMonths(String text) {
        Matcher mKousei = Pattern.compile("一般厚生年金.*?\\n?\\s*([0-9]+)\\s*月").matcher(text);
        Integer kousei = mKousei.find() ? Integer.valueOf(mKousei.group(1)) : null;

        Matcher mKokumin1 = Pattern.compile("第[1１]号被保険者.*?\\n?\\s*([0-9]+)\\s*月").matcher(text);
        Integer kokumin1 = mKokumin1.find() ? Integer.valueOf(mKokumin1.group(1)) : null;

        Matcher mKokumin3 = Pattern.compile("第[3３]号被保険者.*?\\n?\\s*([0-9]+)\\s*月").matcher(text);
        Integer kokumin3 = mKokumin3.find() ? Integer.valueOf(mKokumin3.group(1)) : null;

        Matcher mKokuminTot = Pattern.compile("国民年金(?:計|保険)?.*?\\n?\\s*([0-9]+)\\s*月").matcher(text);
        Integer kokuminTot = mKokuminTot.find() ? Integer.valueOf(mKokuminTot.group(1)) : null;

        if (kokuminTot == null && (kokumin1 != null || kokumin3 != null)) {
            kokuminTot = (kokumin1 != null ? kokumin1 : 0) + (kokumin3 != null ? kokumin3 : 0);
        }

        Matcher mTot = Pattern.compile("(?:年金加入期間\\s*合計|加入期間合計).*?\\n?\\s*([0-9]+)\\s*月").matcher(text);
        Integer tot = mTot.find() ? Integer.valueOf(mTot.group(1)) : null;
        if (tot == null && (kousei != null || kokuminTot != null)) {
            tot = (kousei != null ? kousei : 0) + (kokuminTot != null ? kokuminTot : 0);
        }

        Matcher mQual = Pattern.compile("受給資格期間.*?\\n?\\s*([0-9]+)\\s*月").matcher(text);
        Integer qual = mQual.find() ? Integer.valueOf(mQual.group(1)) : null;

        if (kousei != null || kokuminTot != null || tot != null) {
            return new EnrolledMonths(
                    kokumin1 != null ? kokumin1 : (kokuminTot != null ? kokuminTot : 0),
                    kokumin3 != null ? kokumin3 : 0,
                    kokuminTot != null ? kokuminTot : 0,
                    kousei,
                    kousei,
                    tot != null ? tot : kousei,
                    qual != null ? qual : tot
            );
        }

        return null;
    }
}

