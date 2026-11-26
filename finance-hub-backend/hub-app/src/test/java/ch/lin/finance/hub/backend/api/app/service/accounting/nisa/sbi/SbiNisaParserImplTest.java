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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;

class SbiNisaParserImplTest {

    private SbiNisaParserImpl parser;

    @BeforeEach
    void setUp() {
        parser = new SbiNisaParserImpl();
    }

    @Test
    @DisplayName("Should successfully parse NISA portfolio mock HTML into snapshot records with fund codes")
    void parse_Success_WithMockPortfolioHtml() throws Exception {
        LocalDate recordDate = LocalDate.of(2025, 7, 15);
        try (InputStream is = getClass().getResourceAsStream("/mock-data/nisa/sbi_nisa_portfolio_mock.html")) {
            assertNotNull(is, "Mock portfolio HTML file must exist in test resources");
            List<NisaSnapshotRecord> records = parser.parse(is, recordDate);

            assertEquals(2, records.size());

            // Record 1: NISA (成長)預り
            NisaSnapshotRecord r1 = records.get(0);
            assertEquals(recordDate, r1.getRecordDate());
            assertEquals("NISA (成長)預り", r1.getAccountType());
            assertEquals("03311187", r1.getFundCode());
            assertEquals("ｅＭＡＸＩＳ　Ｓｌｉｍ　米国株式（Ｓ＆Ｐ５００）", r1.getFundName());
            assertEquals(924141.0, r1.getUnits());
            assertEquals(33843.0, r1.getAverageCost());
            assertEquals(44958.0, r1.getCurrentPrice());
            assertEquals(3127570L, r1.getTotalPrincipal());
            assertEquals(4154753L, r1.getCurrentMarketValue());
            assertEquals(1027183L, r1.getUnrealizedGainLoss());

            // Record 2: NISA (つみたて)預り
            NisaSnapshotRecord r2 = records.get(1);
            assertEquals(recordDate, r2.getRecordDate());
            assertEquals("NISA (つみたて)預り", r2.getAccountType());
            assertEquals("0331418A", r2.getFundCode());
            assertEquals("ｅＭＡＸＩＳ　Ｓｌｉｍ　全世界株式（オール・カントリー）", r2.getFundName());
            assertEquals(2844.0, r2.getUnits());
            assertEquals(24614.0, r2.getAverageCost());
            assertEquals(38334.0, r2.getCurrentPrice());
            assertEquals(7000L, r2.getTotalPrincipal());
            assertEquals(10902L, r2.getCurrentMarketValue());
            assertEquals(3902L, r2.getUnrealizedGainLoss());
        }
    }

    @Test
    @DisplayName("Should successfully parse NISA settings mock HTML into contribution settings with fund codes")
    void parseSettings_Success_WithMockSettingsHtml() throws Exception {
        LocalDate recordDate = LocalDate.of(2025, 7, 15);
        try (InputStream is = getClass().getResourceAsStream("/mock-data/nisa/sbi_nisa_settings_mock.html")) {
            assertNotNull(is, "Mock settings HTML file must exist in test resources");
            List<NisaContributionSetting> settings = parser.parseSettings(is, recordDate);

            assertEquals(2, settings.size());

            // Setting 1: Daily course
            NisaContributionSetting s1 = settings.get(0);
            assertEquals(recordDate, s1.getRecordDate());
            assertEquals("NISA (成長)", s1.getAccountType());
            assertEquals("03311187", s1.getFundCode());
            assertEquals("ｅＭＡＸＩＳ　Ｓｌｉｍ　米国株式（Ｓ＆Ｐ５００）", s1.getFundName());
            assertEquals("毎日", s1.getCourse());
            assertEquals(5000L, s1.getSettingAmount());

            // Setting 2: Monthly course
            NisaContributionSetting s2 = settings.get(1);
            assertEquals(recordDate, s2.getRecordDate());
            assertEquals("NISA (成長)", s2.getAccountType());
            assertEquals("0331418A", s2.getFundCode());
            assertEquals("ｅＭＡＸＩＳ　Ｓｌｉｍ　全世界株式（オール・カントリー）", s2.getFundName());
            assertEquals("毎月", s2.getCourse());
            assertEquals(50000L, s2.getSettingAmount());
        }
    }

    @Test
    @DisplayName("Should correctly parse weekly course setting")
    void parseSettings_WeeklyCourse_ParsesCorrectly() throws Exception {
        String html = "<li class=\"table-row\">"
                + "<div data-testid=\"fundName\"><a href=\"https://member.c.sbisec.co.jp/fund/detail/2931113C\"><p>Fund Weekly</p></a></div>"
                + "<div data-testid=\"accountType_PC\">NISA (つみたて)</div>"
                + "<div data-testid=\"courseType_PC\">毎週</div>"
                + "<div data-testid=\"settingAmount_PC\">10,000円</div>"
                + "</li>";

        try (InputStream is = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))) {
            List<NisaContributionSetting> settings = parser.parseSettings(is, LocalDate.now());
            assertEquals(1, settings.size());
            NisaContributionSetting setting = settings.get(0);
            assertEquals("2931113C", setting.getFundCode());
            assertEquals("毎週", setting.getCourse());
            assertEquals(10000L, setting.getSettingAmount());
        }
    }

    @Test
    @DisplayName("Should return empty list gracefully when parse is called with settings HTML")
    void parse_ReturnsEmpty_WhenGivenSettingsHtml() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/mock-data/nisa/sbi_nisa_settings_mock.html")) {
            List<NisaSnapshotRecord> records = parser.parse(is, LocalDate.now());
            assertTrue(records.isEmpty());
        }
    }

    @Test
    @DisplayName("Should return empty list gracefully when parseSettings is called with portfolio HTML")
    void parseSettings_ReturnsEmpty_WhenGivenPortfolioHtml() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/mock-data/nisa/sbi_nisa_portfolio_mock.html")) {
            List<NisaContributionSetting> settings = parser.parseSettings(is, LocalDate.now());
            assertTrue(settings.isEmpty());
        }
    }

    @Test
    @DisplayName("Should throw HtmlParsingException when an asset row has price labels but missing fund name link")
    void parse_ThrowsHtmlParsingException_WhenAssetRowMissingFundNameLink() {
        String malformedHtml = "<h4 class=\"headline-sm\">NISA (成長)預り</h4>"
                + "<li class=\"table-row\">"
                + "<div data-label=\"評価額\"><p>100,000円</p></div>"
                + "<div data-label=\"取得単價\"><p>50,000円</p></div>"
                + "</li>";

        try (InputStream is = new ByteArrayInputStream(malformedHtml.getBytes(StandardCharsets.UTF_8))) {
            assertThrows(HtmlParsingException.class, () -> parser.parse(is, LocalDate.now()));
        } catch (Exception e) {
            // Stream close exception
        }
    }
}
