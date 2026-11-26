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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsuranceFilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;

class SonyAssuranceAutoHtmlParserTest {

    @Test
    void testCanParseAndParse() throws Exception {
        SonyAssuranceAutoHtmlParser parser = new SonyAssuranceAutoHtmlParser();

        File htmlFile = new File(getClass().getResource("/mock-data/insurance/sony_assurance_auto.html").getFile());
        InsuranceFilePreview preview = new InsuranceFilePreview(htmlFile);

        assertTrue(parser.canParse(preview));

        List<InsurancePolicy> policies = parser.parse(preview);

        printPolicies("Mock Data (sony_assurance_auto.html)", policies);

        assertEquals(1, policies.size());
        InsurancePolicy policy = policies.get(0);

        assertNull(policy.getOwnerUserId());
        assertEquals("ソニー損害保険株式会社", policy.getCompanyName());
        assertEquals("9L924089", policy.getPolicyNumber());
        assertEquals("ソニー損保総合自動車保険", policy.getPolicyName());
        assertEquals(91670L, policy.getAnnualPremium());
        assertEquals(InsurancePolicyType.AUTO, policy.getPolicyType());
        assertEquals(LocalDate.of(2027, 6, 17), policy.getEndDate());
    }

    @Test
    void testCannotParseFireInsurance() throws Exception {
        File fireHtml = new File(getClass().getResource("/mock-data/insurance/sony_assurance.html").getFile());
        SonyAssuranceAutoHtmlParser parser = new SonyAssuranceAutoHtmlParser();
        InsuranceFilePreview preview = new InsuranceFilePreview(fireHtml);
        assertFalse(parser.canParse(preview), "SonyAssuranceAutoHtmlParser must NOT accept fire insurance HTML");
    }

    private void printPolicies(String source, List<InsurancePolicy> policies) {
        System.out.println("--------------------------------------------------");
        System.out.println("  [SonyAssuranceAutoHtmlParser 回傳值: " + source + "]");
        for (int i = 0; i < policies.size(); i++) {
            InsurancePolicy p = policies.get(i);
            System.out.printf("  保單 %d:%n", i + 1);
            System.out.printf("    - policyName    : %s%n", p.getPolicyName());
            System.out.printf("    - policyType    : %s%n", p.getPolicyType());
            System.out.printf("    - annualPremium : %s 円%n", p.getAnnualPremium());
            System.out.printf("    - endDate       : %s%n", p.getEndDate());
        }
        System.out.println("--------------------------------------------------");
    }
}
