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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.sompo;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionPageType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;

class SompoDcStatusParserImplTest {

    private SompoDcStatusParserImpl parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new SompoDcStatusParserImpl();
    }

    @Test
    void testGetPageType() {
        assertThat(parser.getPageType()).isEqualTo(DefinedContributionPageType.STATUS);
    }

    @Test
    void parse_validHtml_shouldReturnAccountSnapshot() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/mock-data/dc/sompo_dc_asset_status.html")) {
            Document doc = Jsoup.parse(is, "Shift_JIS", "");
            LocalDate recordDate = LocalDate.of(2025, 7, 15);
            DefinedContributionAccountSnapshot snapshot = parser.parse(doc, recordDate);

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getRecordDate()).isEqualTo(recordDate);
            assertThat(snapshot.getType()).isEqualTo(DefinedContributionType.CORPORATE_DC);
            assertThat(snapshot.getTotalAssetValue()).isEqualTo(2997252L);
            assertThat(snapshot.getTotalAccumulatedContribution()).isEqualTo(1720000L);
            assertThat(snapshot.getFunds()).hasSize(2);

            ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot fund1 = snapshot
                    .getFunds().get(0);
            assertThat(fund1.getCommodityType()).isEqualTo("外国株式");
            assertThat(fund1.getFundName()).isEqualTo("インデックスファンド海外株式ヘッジなし（ＤＣ専用）");
            assertThat(fund1.getFundCode()).isEqualTo("009911102C");
            assertThat(fund1.getCurrentPrice()).isEqualTo(126210.0);
            assertThat(fund1.getUnits()).isEqualTo(156038.0);
            assertThat(fund1.getCurrentMarketValue()).isEqualTo(1969355L);
            assertThat(fund1.getTotalPrincipal()).isEqualTo(1230969L); // actually 1,230,969 from log!
            assertThat(fund1.getUnrealizedGainLoss()).isEqualTo(738386L); // 738,386

            ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot fund2 = snapshot
                    .getFunds().get(1);
            assertThat(fund2.getCommodityType()).isEqualTo("外国株式");
            assertThat(fund2.getFundName()).isEqualTo("大和住銀ＤＣ外国株式ファンド");
            assertThat(fund2.getFundCode()).isEqualTo("009911102D");
            assertThat(fund2.getCurrentPrice()).isEqualTo(151384.0);
            assertThat(fund2.getUnits()).isEqualTo(67900.0);
            assertThat(fund2.getCurrentMarketValue()).isEqualTo(1027897L);
            assertThat(fund2.getTotalPrincipal()).isEqualTo(586125L);
            assertThat(fund2.getUnrealizedGainLoss()).isEqualTo(441772L);
        }
    }

    @Test
    void testParseInvalidPageShouldFail() throws Exception {
        // We can test with IDECO file which should fail for Sompo parser
        try (InputStream is = getClass().getResourceAsStream("/mock-data/dc/sbi_ideco_asset_status.html")) {
            Document doc = Jsoup.parse(is, "Shift_JIS", "");
            assertThat(parser.canParse(doc)).isFalse();
        }
    }
}
