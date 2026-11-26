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
package ch.lin.finance.hub.backend.api.app.service.currency.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateType;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;

class MurcExcelExchangeRateParserImplTest {

    private MurcExcelExchangeRateParserImpl parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new MurcExcelExchangeRateParserImpl();
    }

    @Test
    void shouldRecognizeValidMurcExcelFiles(@TempDir File tempDir) throws Exception {
        // 1. Valid Excel with MURC 2-tier headers from real sample
        File validExcel = new File("src/test/resources/murc_2025.xls");

        // 2. Invalid Excel with arbitrary non-currency content
        File invalidExcel = new File(tempDir, "arbitrary.xls");
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Title");
            row.createCell(1).setCellValue("Description");
            try (FileOutputStream fos = new FileOutputStream(invalidExcel)) {
                wb.write(fos);
            }
        }

        // 3. Non-Excel file
        File csvFile = new File(tempDir, "statement.csv");
        csvFile.createNewFile();

        assertThat(parser.canParse(validExcel)).isTrue();
        assertThat(parser.canParse(invalidExcel)).isFalse();
        assertThat(parser.canParse(csvFile)).isFalse();
        assertThat(parser.canParse(null)).isFalse();
        assertThat(parser.canParse(new File(tempDir, "non_existent.xls"))).isFalse();
    }

    @Test
    void shouldParseGeneratedMurcExcelWorkbook(@TempDir File tempDir) throws Exception {
        File excelFile = new File(tempDir, "murc_test.xls");
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("data");

            // Row 0: Currency name header (MURC real format)
            // USD anchors at col 2 (TTS=2, TTB=3, TTM=4)
            // EUR anchors at col 5 (TTS=5, TTB=6, TTM=7)
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("2025年");
            headerRow.createCell(2).setCellValue("米ドル（USD）");
            headerRow.createCell(5).setCellValue("ユーロ(EUR)");

            // Row 1: empty / note row (mirrors real file)

            // Row 2: Rate type header
            Row rateTypeRow = sheet.createRow(2);
            rateTypeRow.createCell(0).setCellValue("DATE");
            rateTypeRow.createCell(1).setCellValue("曜");
            rateTypeRow.createCell(2).setCellValue("TTS");
            rateTypeRow.createCell(3).setCellValue("TTB");
            rateTypeRow.createCell(4).setCellValue("TTM");
            rateTypeRow.createCell(5).setCellValue("TTS");
            rateTypeRow.createCell(6).setCellValue("TTB");
            rateTypeRow.createCell(7).setCellValue("TTM");

            // Row 3: Data 1 (2025/01/06)
            Row dataRow1 = sheet.createRow(3);
            dataRow1.createCell(0).setCellValue("2025/01/06");
            dataRow1.createCell(1).setCellValue("月");
            dataRow1.createCell(2).setCellValue(158.73);
            dataRow1.createCell(3).setCellValue(156.73);
            dataRow1.createCell(4).setCellValue(157.73);
            dataRow1.createCell(5).setCellValue(163.96);
            dataRow1.createCell(6).setCellValue(160.96);
            dataRow1.createCell(7).setCellValue(162.46);

            // Row 4: Data 2 (2025/01/07)
            Row dataRow2 = sheet.createRow(4);
            dataRow2.createCell(0).setCellValue("2025/01/07");
            dataRow2.createCell(1).setCellValue("火");
            dataRow2.createCell(2).setCellValue(159.22);
            dataRow2.createCell(3).setCellValue(157.22);
            dataRow2.createCell(4).setCellValue(158.22);
            dataRow2.createCell(5).setCellValue(165.80);
            dataRow2.createCell(6).setCellValue(162.80);
            dataRow2.createCell(7).setCellValue(164.30);

            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        }

        ExchangeRateParseResult result = parser.parse(excelFile);

        assertThat(result).isNotNull();
        List<ExchangeRateRecord> records = result.getRecords();
        // 2 dates × 2 currencies × 3 rate types (TTS/TTB/TTM) = 12 records
        assertThat(records).hasSize(12);

        // Verify USD row1 has all three rate types
        LocalDate date1 = LocalDate.of(2025, 1, 6);
        assertThat(records.stream()
                .filter(r -> r.getBaseCurrency() == CurrencyCode.USD
                        && r.getRecordDate().equals(date1)
                        && r.getRateType() == ExchangeRateType.TTS))
                .hasSize(1)
                .first()
                .extracting(r -> r.getRate())
                .isEqualTo(BigDecimal.valueOf(158.73));

        assertThat(records.stream()
                .filter(r -> r.getBaseCurrency() == CurrencyCode.USD
                        && r.getRecordDate().equals(date1)
                        && r.getRateType() == ExchangeRateType.TTB))
                .hasSize(1)
                .first()
                .extracting(r -> r.getRate())
                .isEqualTo(BigDecimal.valueOf(156.73));

        assertThat(records.stream()
                .filter(r -> r.getBaseCurrency() == CurrencyCode.USD
                        && r.getRecordDate().equals(date1)
                        && r.getRateType() == ExchangeRateType.TTM))
                .hasSize(1)
                .first()
                .extracting(r -> r.getRate())
                .isEqualTo(BigDecimal.valueOf(157.73));

        // Verify EUR is also parsed correctly
        assertThat(records.stream()
                .filter(r -> r.getBaseCurrency() == CurrencyCode.EUR
                        && r.getRecordDate().equals(date1)
                        && r.getRateType() == ExchangeRateType.TTM))
                .hasSize(1)
                .first()
                .extracting(r -> r.getRate())
                .isEqualTo(BigDecimal.valueOf(162.46));

        // Verify source and target currency
        assertThat(records).allMatch(r -> r.getSource().equals("MURC"));
        assertThat(records).allMatch(r -> r.getTargetCurrency() == CurrencyCode.JPY);
    }

    @Test
    void shouldParseActualMurcFileIfPresent() throws Exception {
        File sampleFile = new File(
                "/Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/data/exchange_rates/murc_2025.xls");
        if (sampleFile.exists()) {
            ExchangeRateParseResult result = parser.parse(sampleFile);
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isNotEmpty();
        }
    }
}
