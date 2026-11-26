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
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsuranceFilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;

class SonyLifeDetailHtmlParserTest {

    @Test
    void testSonyLifeDetailParser() throws Exception {
        File file = new File(getClass().getResource("/mock-data/insurance/sony_life_detail.html").getFile());

        InsuranceFilePreview preview = new InsuranceFilePreview(file);
        SonyLifeDetailHtmlParser parser = new SonyLifeDetailHtmlParser();

        assertTrue(parser.canParse(preview), "Parser should be able to parse the detail info page");

        List<InsurancePolicy> policies = parser.parse(preview);
        assertEquals(1, policies.size());

        InsurancePolicy policy = policies.get(0);
        assertNull(policy.getOwnerUserId());
        assertEquals("4421496507", policy.getPolicyNumber());
        assertEquals("家族収入保険（非喫煙優良体料率／最低支払保証期間２年）６５歳満了", policy.getPolicyName());
        assertEquals(0L, policy.getAnnualPremium());
        assertEquals(LocalDate.of(2049, 8, 31), policy.getEndDate());
        assertEquals(InsurancePolicyType.LIFE, policy.getPolicyType());
    }

    @Test
    void testSummaryHtmlCannotBeParsedByDetailParser() throws Exception {
        File file = new File(getClass().getResource("/mock-data/insurance/sony_life_summary.html").getFile());

        InsuranceFilePreview preview = new InsuranceFilePreview(file);
        SonyLifeDetailHtmlParser parser = new SonyLifeDetailHtmlParser();

        assertFalse(parser.canParse(preview), "Detail parser should NOT parse the summary page");
    }

    @Test
    void testFireHtmlCannotBeParsed() throws Exception {
        File file = new File(getClass().getResource("/mock-data/insurance/sony_assurance.html").getFile());

        InsuranceFilePreview preview = new InsuranceFilePreview(file);
        SonyLifeDetailHtmlParser parser = new SonyLifeDetailHtmlParser();

        assertFalse(parser.canParse(preview), "Detail parser should NOT parse fire insurance page");
    }

    @Test
    void testAutoHtmlCannotBeParsed() throws Exception {
        File file = new File(getClass().getResource("/mock-data/insurance/sony_assurance_auto.html").getFile());

        InsuranceFilePreview preview = new InsuranceFilePreview(file);
        SonyLifeDetailHtmlParser parser = new SonyLifeDetailHtmlParser();

        assertFalse(parser.canParse(preview), "Detail parser should NOT parse auto insurance page");
    }

    @Test
    void testPolicyMergeCompletesInformation() {
        SonyLifeDetailHtmlParser detailParser = new SonyLifeDetailHtmlParser();
        SonyLifeSummaryHtmlParser summaryParser = new SonyLifeSummaryHtmlParser();

        InsurancePolicy summaryPolicy = new InsurancePolicy(
                "ソニー生命保険株式会社",
                InsurancePolicyType.LIFE,
                "4421496507",
                "家族収入保険（非喫煙優良体料率／最低支払保証期間２年）６５歳満了",
                47760L,
                null);

        InsurancePolicy detailPolicy = new InsurancePolicy(
                "ソニー生命保険株式会社",
                InsurancePolicyType.LIFE,
                "4421496507",
                "家族収入保険（非喫煙優良体料率／最低支払保証期間２年）６５歳満了",
                0L,
                LocalDate.of(2049, 8, 31));

        // Parser strategy: merge detail into summary (when Detail page is uploaded)
        detailParser.mergePolicy(summaryPolicy, detailPolicy);
        assertEquals("ソニー生命保険株式会社", summaryPolicy.getCompanyName());
        assertEquals("4421496507", summaryPolicy.getPolicyNumber());
        assertEquals("家族収入保険（非喫煙優良体料率／最低支払保証期間２年）６５歳満了", summaryPolicy.getPolicyName());
        assertEquals(47760L, summaryPolicy.getAnnualPremium(), "Annual premium from summary should be preserved");
        assertEquals(LocalDate.of(2049, 8, 31), summaryPolicy.getEndDate(), "End date from detail should be populated");

        // Parser strategy: reverse merge summary into detail (when Summary page is
        // uploaded)
        InsurancePolicy anotherDetail = new InsurancePolicy(
                "ソニー生命保険株式会社",
                InsurancePolicyType.LIFE,
                "4421496507",
                "家族収入保険（非喫煙優良体料率／最低支払保証期間２年）６５歳満了",
                0L,
                LocalDate.of(2049, 8, 31));

        summaryParser.mergePolicy(anotherDetail, summaryPolicy);
        assertEquals("ソニー生命保険株式会社", anotherDetail.getCompanyName());
        assertEquals("4421496507", anotherDetail.getPolicyNumber());
        assertEquals("家族収入保険（非喫煙優良体料率／最低支払保証期間２年）６５歳満了", anotherDetail.getPolicyName());
        assertEquals(47760L, anotherDetail.getAnnualPremium(), "Annual premium from summary should populate detail");
        assertEquals(LocalDate.of(2049, 8, 31), anotherDetail.getEndDate(), "End date should be preserved");

        // Domain entity merge: unconditional overwrite (pure DDD behavior)
        InsurancePolicy domainA = new InsurancePolicy(
                "ソニー生命保険株式会社", InsurancePolicyType.LIFE, "P1", "NameA", 50000L, LocalDate.of(2030, 1, 1));
        InsurancePolicy domainB = new InsurancePolicy(
                "ソニー損害保険株式会社", InsurancePolicyType.FIRE, "P2", "NameB", 0L, null);
        domainA.merge(domainB);
        assertEquals("ソニー損害保険株式会社", domainA.getCompanyName());
        assertEquals(InsurancePolicyType.FIRE, domainA.getPolicyType());
        assertEquals("P2", domainA.getPolicyNumber());
        assertEquals("NameB", domainA.getPolicyName());
        assertEquals(0L, domainA.getAnnualPremium());
        assertNull(domainA.getEndDate());
    }
}
