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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.sony;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;

class SonyBankMortgageHtmlParserImplTest {

    private SonyBankMortgageHtmlParserImpl parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new SonyBankMortgageHtmlParserImpl();
    }

    @Test
    void testParse_LoanTermMonthsCalculatedUsingYearMonth_AvoidsDayTruncation() throws IOException {
        // Arrange: Start day (25th) is greater than end day (10th).
        // Under LocalDate ChronoUnit.MONTHS, between(2025-03-25, 2060-03-10) would be truncated to 419 months.
        // Under YearMonth, it must evaluate to exact 420 months (35 years).
        String html = """
                <html>
                <head><title>ソニー銀行 住宅ローン 詳細</title></head>
                <body>
                <table>
                    <tr><th>契約番号</th><td>1</td></tr>
                    <tr><th>商品名</th><td>変動金利住宅ローン</td></tr>
                    <tr><th>当初借入日</th><td>2025年3月25日</td></tr>
                    <tr><th>最終返済日</th><td>2060年3月10日</td></tr>
                    <tr><th>当初借入額</th><td>50,000,000円</td></tr>
                    <tr><th>借入残高</th><td>48,000,000円</td></tr>
                    <tr><th>適用金利</th><td>0.500%</td></tr>
                    <tr><th>金利タイプ</th><td>変動金利</td></tr>
                    <tr><th>次回返済額</th><td>129,567円</td></tr>
                </table>
                </body>
                </html>
                """;

        File file = tempDir.resolve("sony_mortgage_detail.html").toFile();
        Files.writeString(file.toPath(), html);

        // Act
        assertTrue(parser.canParse(file));
        MortgageRecord record = parser.parse(file);

        // Assert
        assertNotNull(record);
        assertEquals("ソニー銀行", record.getBankName());
        assertEquals("1", record.getContractNumber());
        assertEquals("変動金利住宅ローン", record.getLoanName());
        assertEquals(LocalDate.of(2025, 3, 25), record.getStartDate());
        assertEquals(LocalDate.of(2060, 3, 10), record.getEndDate());
        assertEquals(420, record.getLoanTermMonths(), "Must be exact 420 months without day-truncation bug");
        assertEquals(50_000_000L, record.getTotalLoanAmount());
        assertEquals(48_000_000L, record.getCurrentBalance());
        assertEquals(0.005, record.getInterestRate(), 1e-6);
        assertEquals(InterestRateType.VARIABLE, record.getRateType());
        assertEquals(129_567L, record.getMonthlyPayment());
    }

    @Test
    void testParse_AlternativeEndDateKey_UsesYearMonth() throws IOException {
        // Arrange: Uses 最終返済予定日 instead of 最終返済日, 30-year span (360 months)
        String html = """
                <html>
                <head><title>住宅ローン（ソニー銀行）</title></head>
                <body>
                <table>
                    <tr><th>商品名</th><td>固定金利住宅ローン</td></tr>
                    <tr><th>当初借入日</th><td>2025年11月28日</td></tr>
                    <tr><th>最終返済予定日</th><td>2055年11月5日</td></tr>
                    <tr><th>当初借入額</th><td>30,000,000円</td></tr>
                    <tr><th>借入残高</th><td>30,000,000円</td></tr>
                    <tr><th>適用金利（年利）</th><td>1.250%</td></tr>
                    <tr><th>金利タイプ</th><td>固定金利</td></tr>
                    <tr><th>次回返済額</th><td>99,800円</td></tr>
                </table>
                </body>
                </html>
                """;

        File file = tempDir.resolve("sony_mortgage_scheduled.html").toFile();
        Files.writeString(file.toPath(), html);

        // Act
        MortgageRecord record = parser.parse(file);

        // Assert
        assertEquals(360, record.getLoanTermMonths(), "Must be exact 360 months");
        assertEquals(LocalDate.of(2025, 11, 28), record.getStartDate());
        assertEquals(LocalDate.of(2055, 11, 5), record.getEndDate());
        assertEquals(InterestRateType.FIXED, record.getRateType());
    }

    @Test
    void testCanParse_ExcludesHistoryPagesAndNonSonyPages() throws IOException {
        String historyHtml = """
                <html>
                <head><title>ソニー銀行 住宅ローン 返済履歴</title></head>
                <body>
                <table>
                    <tr><th>当初借入額</th><td>50,000,000円</td></tr>
                    <tr><th>借入残高</th><td>48,000,000円</td></tr>
                </table>
                </body>
                </html>
                """;
        File historyFile = tempDir.resolve("sony_history.html").toFile();
        Files.writeString(historyFile.toPath(), historyHtml);

        assertFalse(parser.canParse(historyFile), "Must reject history pages");

        String otherBankHtml = """
                <html>
                <head><title>三井住友銀行 住宅ローン 詳細</title></head>
                <body></body>
                </html>
                """;
        File otherFile = tempDir.resolve("other_bank.html").toFile();
        Files.writeString(otherFile.toPath(), otherBankHtml);

        assertFalse(parser.canParse(otherFile), "Must reject non-Sony pages");
    }

    @Test
    void testParse_MissingMandatoryFields_ThrowsException() throws IOException {
        String invalidHtml = """
                <html>
                <head><title>ソニー銀行 住宅ローン</title></head>
                <body>
                <table>
                    <tr><th>当初借入額</th><td></td></tr>
                    <tr><th>借入残高</th><td>10,000,000円</td></tr>
                </table>
                </body>
                </html>
                """;
        File invalidFile = tempDir.resolve("invalid.html").toFile();
        Files.writeString(invalidFile.toPath(), invalidHtml);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(invalidFile));
    }
}
