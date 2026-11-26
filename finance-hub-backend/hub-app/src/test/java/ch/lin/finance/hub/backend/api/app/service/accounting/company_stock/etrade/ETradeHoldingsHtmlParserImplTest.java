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
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeHoldingsParseResult;
import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeEsppStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRsuStockLot;

public class ETradeHoldingsHtmlParserImplTest {

    private ETradeHoldingsHtmlParserImpl parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new ETradeHoldingsHtmlParserImpl();
    }

    private File getResourceFile(String fileName) {
        URL url = getClass().getClassLoader().getResource(fileName);
        assertNotNull(url, "Resource file not found: " + fileName);
        return new File(url.getFile());
    }

    @Test
    void testCanParse() throws Exception {
        File file = getResourceFile("mock-data/etrade/holdings.html");
        boolean canParse = parser.canParse(file);
        assertTrue(canParse, "Parser should be able to parse holdings.html");
    }

    @Test
    void testCannotParseBenefitHistory() throws Exception {
        File file = getResourceFile("mock-data/etrade/benefitHistory.html");
        boolean canParse = parser.canParse(file);
        assertFalse(canParse, "Parser should NOT be able to parse benefitHistory.html");
    }

    @Test
    void testParse() throws Exception {
        File file = getResourceFile("mock-data/etrade/holdings.html");

        LocalDate recordDate = LocalDate.of(2025, 2, 28);
        ETradeHoldingsParseResult result = parser.parse(file, recordDate);

        assertThat(result).isNotNull();

        // Lots check (1 ESPP, 1 RSU)
        assertThat(result.getLots()).hasSize(2);
        assertThat(result.getLots().get(0)).isInstanceOf(ETradeEsppStockLot.class);
        assertThat(result.getLots().get(1)).isInstanceOf(ETradeRsuStockLot.class);

        // Cash transactions check (1 Dividend)
        assertThat(result.getCashTransactions()).hasSize(1);
    }

    @Test
    void testParseWithRsuTranches() throws Exception {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>E*TRADE - Stock Plan Overview</title></head>
            <body>
                <div>Total Account Value $5,000.00</div>
                <div>Current Account Value $5,000.00</div>
                <div>Potential Benefit Value $0.00</div>
                <table>
                    <tr>
                        <th>Grant Number</th>
                        <th>Type</th>
                        <th>Vest Period</th>
                        <th>Vest Date</th>
                        <th>Release Date</th>
                        <th>Sellable Qty.</th>
                        <th>Pending Sale Qty.</th>
                        <th>Est. Cost Basis(per share)</th>
                        <th>Est. Market Value</th>
                        <th>Expected Gain/Loss (per share)</th>
                        <th>Capital Gains Status</th>
                    </tr>
                    <tr>
                        <td>00136443</td>
                        <td>RS</td>
                        <td>1</td>
                        <td>11/22/2022</td>
                        <td>11/22/2022</td>
                        <td>28.00</td>
                        <td>0.00</td>
                        <td>$149.25</td>
                        <td>$4,573.24</td>
                        <td>+$14.08</td>
                        <td>Long Term</td>
                    </tr>
                </table>
            </body>
            </html>
            """;

        File tempFile = File.createTempFile("holdings_tranche_test", ".html");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), html);

        ETradeHoldingsParseResult result = parser.parse(tempFile, LocalDate.of(2025, 2, 28));

        assertThat(result).isNotNull();
        assertThat(result.getLots()).hasSize(1);
        assertThat(result.getLots().get(0)).isInstanceOf(ETradeRsuStockLot.class);

        ETradeRsuStockLot rsuLot = (ETradeRsuStockLot) result.getLots().get(0);
        assertThat(rsuLot.getGrantNumber()).isEqualTo("00136443");
        assertThat(rsuLot.getVestPeriod()).isEqualTo(1);
        assertThat(rsuLot.getVestDate()).isEqualTo(LocalDate.of(2022, 11, 22));
        assertThat(rsuLot.getSellableQuantity()).isEqualTo(28.00);
        assertThat(rsuLot.getEstCostBasis().getAmount()).isEqualByComparingTo(new BigDecimal("149.25"));
        assertThat(rsuLot.getExpectedGainLossPerShare().getAmount()).isEqualByComparingTo(new BigDecimal("14.08"));
        assertThat(rsuLot.getCapitalGainsStatus()).isEqualTo("Long Term");
        assertThat(rsuLot.getExpectedGainLoss().getAmount()).isEqualByComparingTo(new BigDecimal("394.24"));
    }

    @Test
    void testExtractSymbol_withDropdownButton() {
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
        String html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div>Some other text Stock Plan (CDNS) -4184 header</div>
            </body>
            </html>
            """;
        Document doc = Jsoup.parse(html);
        assertThat(parser.extractSymbol(doc)).isEqualTo("CDNS");
    }

    @Test
    void testExtractSymbol_unknown() {
        String html = "<html><body><div>No stock plan info</div></body></html>";
        Document doc = Jsoup.parse(html);
        assertThat(parser.extractSymbol(doc)).isEqualTo("UNKNOWN");
    }

    @Test
    void testParse_extractsSymbolIntoLots() throws Exception {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>E*TRADE - Stock Plan Overview</title></head>
            <body>
                <div data-test-id="Stockplan Account Drop down">
                    <button id="sp-account-dropdown" class="dropdown-toggle">
                        <span>Stock Plan (CRM) -4258</span>
                    </button>
                </div>
                <div>Total Account Value $1,000.00</div>
                <div>Current Account Value $1,000.00</div>
                <table>
                    <tr>
                        <th>Purchase Date</th>
                        <th>Purchase Price</th>
                        <th>Purchased Qty.</th>
                        <th>Sellable Qty.</th>
                        <th>Expected Gain/Loss</th>
                        <th>Est. Market Value</th>
                    </tr>
                    <tr>
                        <td>01/01/2024</td>
                        <td>$10.00</td>
                        <td>10.00</td>
                        <td>10.00</td>
                        <td>$100.00</td>
                        <td>$150.00</td>
                    </tr>
                </table>
            </body>
            </html>
            """;
        File tempFile = File.createTempFile("holdings_symbol_test", ".html");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), html);

        ETradeHoldingsParseResult result = parser.parse(tempFile, LocalDate.of(2025, 2, 28));
        assertThat(result.getLots()).hasSize(1);
        assertThat(result.getLots().get(0).getSymbol()).isEqualTo("CRM");
    }

    @Test
    void testParse_missingTotalAccountValue_throwsHtmlParsingException() throws Exception {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>E*TRADE - Stock Plan Overview</title></head>
            <body>
                <div>Current Account Value $1,000.00</div>
            </body>
            </html>
            """;
        File tempFile = File.createTempFile("missing_total_val", ".html");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), html);

        assertThatThrownBy(() -> parser.parse(tempFile, LocalDate.of(2025, 2, 28)))
                .isInstanceOf(HtmlParsingException.class)
                .hasMessageContaining("Missing mandatory label: Total Account Value");
    }

    @Test
    void testParse_missingCurrentAccountValue_throwsHtmlParsingException() throws Exception {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>E*TRADE - Stock Plan Overview</title></head>
            <body>
                <div>Total Account Value $1,000.00</div>
            </body>
            </html>
            """;
        File tempFile = File.createTempFile("missing_current_val", ".html");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), html);

        assertThatThrownBy(() -> parser.parse(tempFile, LocalDate.of(2025, 2, 28)))
                .isInstanceOf(HtmlParsingException.class)
                .hasMessageContaining("Missing mandatory label: Current Account Value");
    }

    @Test
    void testParse_validZeroValues_parsedSuccessfully() throws Exception {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>E*TRADE - Stock Plan Overview</title></head>
            <body>
                <div>Total Account Value $0.00</div>
                <div>Current Account Value $0.00</div>
            </body>
            </html>
            """;
        File tempFile = File.createTempFile("valid_zero_val", ".html");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), html);

        ETradeHoldingsParseResult result = parser.parse(tempFile, LocalDate.of(2025, 2, 28));
        assertThat(result).isNotNull();
        assertThat(result.getSnapshot().getTotalAccountValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getSnapshot().getCurrentAccountValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
