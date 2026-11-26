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
package ch.lin.finance.hub.backend.api.app.service.accounting.insurance.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsuranceFilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;

class SonyLifeSummaryHtmlParserTest {

    @Test
    void testSonyLifeParser() throws Exception {
        File file = new File(getClass().getResource("/mock-data/insurance/sony_life_summary.html").getFile());

        InsuranceFilePreview preview = new InsuranceFilePreview(file);
        SonyLifeSummaryHtmlParser parser = new SonyLifeSummaryHtmlParser();

        assertTrue(parser.canParse(preview), "Parser should be able to parse the basic info page");

        List<InsurancePolicy> policies = parser.parse(preview);
        assertEquals(1, policies.size());

        InsurancePolicy policy = policies.get(0);
        assertNull(policy.getOwnerUserId());
        assertEquals("ソニー生命保険株式会社", policy.getCompanyName());
        assertEquals("4025461849", policy.getPolicyNumber());
        assertEquals("終身がん保険（０８）６５歳払込済／低解約返戻金特則／診断給付金１００倍", policy.getPolicyName());
        assertEquals(41496L, policy.getAnnualPremium()); // 3458 * 12
        assertEquals(InsurancePolicyType.NURSING_CARE, policy.getPolicyType()); // Contains "がん"
    }

    @Test
    void testDetailHtmlCannotBeParsed() throws Exception {
        File file = new File(getClass().getResource("/mock-data/insurance/sony_life_detail.html").getFile());

        InsuranceFilePreview preview = new InsuranceFilePreview(file);
        SonyLifeSummaryHtmlParser parser = new SonyLifeSummaryHtmlParser();

        assertFalse(parser.canParse(preview), "Parser should NOT parse the detail page");
    }

    @Test
    void testPaidUpStatusResultsInZeroAnnualPremium(@TempDir Path tempDir) throws Exception {
        String html = """
                <html>
                <head><title>契約内容照会 | ソニー生命保険株式会社</title></head>
                <body>
                    <span id="id_polNo">4104379511</span>
                    <span id="headform1:id_ist">継続中（払済保険）</span>
                    <span id="headform1:id_brdIsfNmTop">米ドル建生前給付終身保険</span>
                    <span id="headform1:id_nxtPtdPr">-米ドル</span>
                    <span id="headform1:id_mdeQt">月払</span>
                </body>
                </html>
                """;
        java.nio.file.Path file = tempDir.resolve("paid_up.html");
        Files.writeString(file, html);

        InsuranceFilePreview preview = new InsuranceFilePreview(file.toFile());
        SonyLifeSummaryHtmlParser parser = new SonyLifeSummaryHtmlParser();

        assertTrue(parser.canParse(preview));
        List<InsurancePolicy> policies = parser.parse(preview);
        assertEquals(1, policies.size());
        assertEquals(0L, policies.get(0).getAnnualPremium(), "Paid-up policy must have 0 annual premium");
    }

    @Test
    void testLumpSumFrequencyResultsInZeroAnnualPremium(@TempDir Path tempDir) throws Exception {
        String html = """
                <html>
                <head><title>契約内容照会 | ソニー生命保険株式会社</title></head>
                <body>
                    <span id="id_polNo">9999999999</span>
                    <span id="headform1:id_ist">継続中</span>
                    <span id="headform1:id_brdIsfNmTop">一時払終身保険</span>
                    <span id="headform1:id_nxtPtdPr">2,000,000円</span>
                    <span id="headform1:id_mdeQt">一時払</span>
                </body>
                </html>
                """;
        java.nio.file.Path file = tempDir.resolve("lump_sum.html");
        Files.writeString(file, html);

        InsuranceFilePreview preview = new InsuranceFilePreview(file.toFile());
        SonyLifeSummaryHtmlParser parser = new SonyLifeSummaryHtmlParser();

        assertTrue(parser.canParse(preview));
        List<InsurancePolicy> policies = parser.parse(preview);
        assertEquals(1, policies.size());
        assertEquals(0L, policies.get(0).getAnnualPremium(), "One-time lump sum policy must have 0 annual premium");
    }

    @Test
    void testSemiAnnualAndAnnualPayment(@TempDir Path tempDir) throws Exception {
        String htmlSemi = """
                <html>
                <head><title>契約内容照会 | ソニー生命保険株式会社</title></head>
                <body>
                    <span id="id_polNo">1111111111</span>
                    <span id="headform1:id_ist">継続中</span>
                    <span id="headform1:id_brdIsfNmTop">普通定期保険</span>
                    <span id="headform1:id_nxtPtdPr">50,000円</span>
                    <span id="headform1:id_mdeQt">半年払</span>
                </body>
                </html>
                """;
        java.nio.file.Path fileSemi = tempDir.resolve("semi_annual.html");
        Files.writeString(fileSemi, htmlSemi);

        SonyLifeSummaryHtmlParser parser = new SonyLifeSummaryHtmlParser();
        List<InsurancePolicy> policiesSemi = parser.parse(new InsuranceFilePreview(fileSemi.toFile()));
        assertEquals(100000L, policiesSemi.get(0).getAnnualPremium(), "Semi-annual 50,000 should be 100,000 annual");

        String htmlAnnual = """
                <html>
                <head><title>契約内容照会 | ソニー生命保険株式会社</title></head>
                <body>
                    <span id="id_polNo">2222222222</span>
                    <span id="headform1:id_ist">継続中</span>
                    <span id="headform1:id_brdIsfNmTop">普通定期保険</span>
                    <span id="headform1:id_nxtPtdPr">100,000円</span>
                    <span id="headform1:id_mdeQt">年払</span>
                </body>
                </html>
                """;
        java.nio.file.Path fileAnnual = tempDir.resolve("annual.html");
        Files.writeString(fileAnnual, htmlAnnual);

        List<InsurancePolicy> policiesAnnual = parser.parse(new InsuranceFilePreview(fileAnnual.toFile()));
        assertEquals(100000L, policiesAnnual.get(0).getAnnualPremium(), "Annual 100,000 should be 100,000 annual");
    }
}
