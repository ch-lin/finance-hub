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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.sbi;

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

class SbiIdecoStatusParserImplTest {

    private SbiIdecoStatusParserImpl parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new SbiIdecoStatusParserImpl();
    }

    @Test
    void testGetPageType() {
        assertThat(parser.getPageType()).isEqualTo(DefinedContributionPageType.STATUS);
    }

    @Test
    void testParseValidIdecoAssetStatusHtml() throws Exception {
        LocalDate recordDate = LocalDate.of(2025, 7, 15);
        try (InputStream is = getClass().getResourceAsStream("/mock-data/dc/sbi_ideco_asset_status.html")) {
            Document doc = Jsoup.parse(is, "Shift_JIS", "");
            DefinedContributionAccountSnapshot snapshot = parser.parse(doc, recordDate);

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getRecordDate()).isEqualTo(recordDate);
            assertThat(snapshot.getType()).isEqualTo(DefinedContributionType.IDECO);
        }
    }

    @Test
    void testParseInvalidPageShouldFail() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/mock-data/dc/sbi_ideco_home.html")) {
            Document doc = Jsoup.parse(is, "Shift_JIS", "");
            assertThat(parser.canParse(doc)).isFalse();
        }
    }
}
