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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;

@SpringBootTest(classes = { NenkinPdfParser.class })
public class NenkinPdfParserTest {

    @Autowired
    private NenkinPdfParser parser;

    @Test
    void testParse() throws IOException {
        File pdfFile = new File(getClass().getResource("/mock-data/pension/nenkin.pdf").getFile());
        assertNotNull(parser);
        
        PensionRecord record = parser.parse(pdfFile);
        
        assertNotNull(record);
        assertNotNull(record.getRecordDate());
        assertEquals(161142L, record.getBasicPensionAmount());
        assertEquals(341297L, record.getKouseiPensionAmount());
        assertEquals(502439L, record.getTotalPensionAmount());
        
        // These are heuristic but let's assert what we expect from the OCR
        assertEquals(93, record.getKouseiTotalMonths());
        assertEquals(5835228L, record.getKouseiPremiumPaid());
    }

    @Test
    void testExtractRecordDateFromDataCreationDate() {
        String text = "納付記録がデータに反映されるまで日数がかかることがあります。\n"
                + "この定期便は、下記データ作成日時点の前々月までの記録をお知らせしています。\n"
                + "【データ作成日】\n"
                + "※厚生年金保険料は、被保険者と事業主が折半して負担することとされています。\n"
                + "0 0 0 0\n"
                + "93\n"
                + " 161,142\n"
                + " 341,297\n"
                + " 502,439\n"
                + "令和 7 11 25";
        java.time.LocalDate date = parser.extractRecordDate(text, null);
        assertEquals(java.time.LocalDate.of(2025, 11, 25), date);
    }

    @Test
    void testExtractRecordDateFromDataCreationDateWithIdeographicSpaces() {
        String text = "公務員厚生年金期間 0 円 【デ ー タ 作 成 日】\n"
                + "国民年金および 公務員厚生年金期間 私学共済厚生年金期間\n"
                + "私学共済厚生年金期間 0 円 一般厚生年金期間 （国家公務員・地方公務員） （私立学校の教職員）\n"
                + "（１）と（２）の合計 502,439 円 令　和　 　7年1　1月2　5日 年 月 日 年 月 日";
        java.time.LocalDate date = parser.extractRecordDate(text, null);
        assertEquals(java.time.LocalDate.of(2025, 11, 25), date);
    }

    @Test
    void testExtractRecordDateFromDownloadDate() {
        String text = "基礎年金番号 ねんきん定期便\n"
                + "ダウンロード日\n"
                + "令和 8 6 7";
        java.time.LocalDate date = parser.extractRecordDate(text, null);
        assertEquals(java.time.LocalDate.of(2026, 6, 7), date);
    }

    @Test
    void testExtractRecordDateFromCurrentRecordAfter() {
        String text = "電子版「被保険者記録照会回答票」\n"
                + "⑮　　　　年　　月　　日現在の加入記録です。\n"
                + "東京都町田市\n"
                + "令和 8 6 5\n"
                + "昭和 59 1 9";
        java.time.LocalDate date = parser.extractRecordDate(text, null);
        assertEquals(java.time.LocalDate.of(2026, 6, 5), date);
    }

    @Test
    void testExtractRecordDateFromCurrentRecordBefore() {
        String text = "電子版「被保険者記録照会回答票」\n"
                + "令和 8 6 5 現在の加入記録です。";
        java.time.LocalDate date = parser.extractRecordDate(text, null);
        assertEquals(java.time.LocalDate.of(2026, 6, 5), date);
    }

    @Test
    void testExtractRecordDateFromEraGannen() {
        String text = "ダウンロード日 令和 元 5 1";
        java.time.LocalDate date = parser.extractRecordDate(text, null);
        assertEquals(java.time.LocalDate.of(2019, 5, 1), date);
    }

    @Test
    void testExtractRecordDateFallbackToFileModifiedTime() throws IOException {
        java.io.File tempFile = java.io.File.createTempFile("nenkin-test", ".pdf");
        try {
            java.time.LocalDate targetDate = java.time.LocalDate.of(2025, 3, 15);
            long epochMillis = targetDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            tempFile.setLastModified(epochMillis);

            java.time.LocalDate date = parser.extractRecordDate("無任何可識別日期的內文", tempFile);
            assertEquals(targetDate, date);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void testExtractRecordDateFallbackToNowWhenNoFile() {
        java.time.LocalDate date = parser.extractRecordDate("無任何可識別日期的內文", null);
        assertEquals(java.time.LocalDate.now(), date);
    }

    @Test
    void testNoArbitraryDateMatchingWithoutContext() {
        // Document containing personal birthday and job history dates without anchor keywords
        String text = "生年月日 昭和 59 1 9\n入社日 平成 30 1 1\n退職日 令和 3 10 31";
        java.time.LocalDate date = parser.extractRecordDate(text, null);
        // Must NOT match birthday or job dates, must fall back to now (or file date)
        assertEquals(java.time.LocalDate.now(), date);
    }

    @Test
    void testExtractEnrolledMonthsTeikibinTable_ZeroKokumin() {
        String text = "２．これまでの年金加入期間\n"
                + "0 0 0 0\n"
                + "93 0 0\n"
                + "93 0 93\n"
                + "３．これまでの加入実績に応じた年金額";
        NenkinPdfParser.EnrolledMonths months = parser.extractEnrolledMonths(text);
        assertNotNull(months);
        assertEquals(0, months.kokuminCategory1Months());
        assertEquals(0, months.kokuminCategory3Months());
        assertEquals(0, months.kokuminTotalMonths());
        assertEquals(93, months.kouseiGeneralMonths());
        assertEquals(93, months.kouseiTotalMonths());
        assertEquals(93, months.totalEnrolledMonths());
        assertEquals(93, months.qualifyingMonths());
    }

    @Test
    void testExtractEnrolledMonthsTeikibinTable_WithKokumin() {
        // User with 24 months Kokumin Cat 1 and 120 months Kousei -> total 144
        String text = "２．これまでの年金加入期間\n"
                + "24 0 0 24\n"
                + "120 0 0\n"
                + "144 0 144\n"
                + "３．これまでの加入実績に応じた年金額";
        NenkinPdfParser.EnrolledMonths months = parser.extractEnrolledMonths(text);
        assertNotNull(months);
        assertEquals(24, months.kokuminCategory1Months());
        assertEquals(0, months.kokuminCategory3Months());
        assertEquals(24, months.kokuminTotalMonths());
        assertEquals(120, months.kouseiGeneralMonths());
        assertEquals(120, months.kouseiTotalMonths());
        assertEquals(144, months.totalEnrolledMonths());
        assertEquals(144, months.qualifyingMonths());
    }

    @Test
    void testExtractEnrolledMonthsInquiryForm() {
        String text = "電子版「被保険者記録照会回答票」\n"
                + "101 101 0 0 ( 0) ( 0) 101\n";
        NenkinPdfParser.EnrolledMonths months = parser.extractEnrolledMonths(text);
        assertNotNull(months);
        assertEquals(0, months.kokuminTotalMonths());
        assertEquals(101, months.kouseiGeneralMonths());
        assertEquals(101, months.kouseiTotalMonths());
        assertEquals(101, months.totalEnrolledMonths());
        assertEquals(101, months.qualifyingMonths());
    }

    @Test
    void testExtractEnrolledMonthsRegex_Decoupled() {
        String text = "第1号被保険者 36 月\n一般厚生年金 84 月\n年金加入期間合計 120 月\n受給資格期間 120 月";
        NenkinPdfParser.EnrolledMonths months = parser.extractEnrolledMonths(text);
        assertNotNull(months);
        assertEquals(36, months.kokuminCategory1Months());
        assertEquals(36, months.kokuminTotalMonths());
        assertEquals(84, months.kouseiGeneralMonths());
        assertEquals(84, months.kouseiTotalMonths());
        assertEquals(120, months.totalEnrolledMonths());
        assertEquals(120, months.qualifyingMonths());
    }
}
