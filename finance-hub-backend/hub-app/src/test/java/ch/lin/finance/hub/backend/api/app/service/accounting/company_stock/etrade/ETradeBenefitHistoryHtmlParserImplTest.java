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
import java.net.URL;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeBenefitHistoryParseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitEsppPurchase;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuEvent;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuVestSchedule;

class ETradeBenefitHistoryHtmlParserImplTest {

    @Test
    void canParse_withValidFile_returnsTrue() throws Exception {
        ETradeBenefitHistoryHtmlParserImpl parser = new ETradeBenefitHistoryHtmlParserImpl();
        File file = getResourceFile("mock-data/etrade/benefitHistory.html");
        assertTrue(parser.canParse(file));
    }

    @Test
    void canParse_withHoldingsFile_returnsFalse() throws Exception {
        ETradeBenefitHistoryHtmlParserImpl parser = new ETradeBenefitHistoryHtmlParserImpl();
        File file = getResourceFile("mock-data/etrade/holdings.html");
        assertFalse(parser.canParse(file));
    }

    @Test
    void parse_withValidFile_extractsAllDataCorrectly() throws Exception {
        ETradeBenefitHistoryHtmlParserImpl parser = new ETradeBenefitHistoryHtmlParserImpl();
        File file = getResourceFile("mock-data/etrade/benefitHistory.html");
        LocalDate recordDate = LocalDate.of(2025, 7, 15);

        ETradeBenefitHistoryParseResult result = parser.parse(file, recordDate);

        // Assert ESPP Purchases
        assertThat(result.getEsppPurchases()).hasSize(1);
        ETradeBenefitEsppPurchase firstEspp = result.getEsppPurchases().get(0);
        assertThat(firstEspp.getPurchaseDate()).isEqualTo(LocalDate.of(2023, 3, 1));
        assertThat(firstEspp.getPurchasedQty().doubleValue()).isEqualTo(10.0);
        assertThat(firstEspp.getGrantDate()).isEqualTo(LocalDate.of(2022, 9, 1));
        assertThat(firstEspp.getQualifiedPlan()).isTrue();

        // Assert RSU Grants
        assertThat(result.getRsuGrants()).hasSize(1);

        // Assert RSU Events
        assertThat(result.getRsuEvents()).hasSize(1);
        ETradeBenefitRsuEvent firstEvent = result.getRsuEvents().get(0);
        assertThat(firstEvent.getGrantNumber()).isEqualTo("G123456");
        assertThat(firstEvent.getEventDate()).isEqualTo(LocalDate.of(2023, 4, 1));
        assertThat(firstEvent.getEventType()).isEqualTo("Shares vested");
        assertThat(firstEvent.getQuantity().doubleValue()).isEqualTo(25.0);

        // Assert RSU Vest Schedules
        assertThat(result.getRsuVestSchedules()).hasSize(1);
        ETradeBenefitRsuVestSchedule firstSchedule = result.getRsuVestSchedules().get(0);
        assertThat(firstSchedule.getGrantNumber()).isEqualTo("G123456");
        assertThat(firstSchedule.getVestDate()).isEqualTo(LocalDate.of(2023, 4, 1));
        assertThat(firstSchedule.getVestPeriod()).isEqualTo(1);
        assertThat(firstSchedule.getGrantedQty().doubleValue()).isEqualTo(50.0);
    }

    @Test
    void testExtractSymbol_withDropdownButton() {
        ETradeBenefitHistoryHtmlParserImpl parser = new ETradeBenefitHistoryHtmlParserImpl();
        String html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div data-test-id="Stockplan Account Drop down">
                    <button class="dropdown-toggle btn btn-lg btn-primary-outline">
                        <span class="text-xs-small">Stock Plan (CRM) -4258</span>
                    </button>
                </div>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html);
        assertThat(parser.extractSymbol(doc)).isEqualTo("CRM");
    }

    @Test
    void testExtractSymbol_withFallback() {
        ETradeBenefitHistoryHtmlParserImpl parser = new ETradeBenefitHistoryHtmlParserImpl();
        String html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div>Some other text Stock Plan (QCOM) -3224 header</div>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html);
        assertThat(parser.extractSymbol(doc)).isEqualTo("QCOM");
    }

    @Test
    void testExtractSymbol_unknown() {
        ETradeBenefitHistoryHtmlParserImpl parser = new ETradeBenefitHistoryHtmlParserImpl();
        String html = "<html><body><div>No stock plan info</div></body></html>";
        Document doc = Jsoup.parse(html);
        assertThat(parser.extractSymbol(doc)).isEqualTo("UNKNOWN");
    }

    private File getResourceFile(String fileName) {
        URL url = getClass().getClassLoader().getResource(fileName);
        assertNotNull(url, "Resource file not found: " + fileName);
        return new File(url.getFile());
    }
}
