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

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsuranceFilePreview;
import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsuranceParser;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;

@Component
public class SonyLifeSummaryHtmlParser implements InsuranceParser {

    public static final String COMPANY_NAME = "ソニー生命保険株式会社";

    @Override
    public boolean canParse(InsuranceFilePreview preview) {
        Document doc = preview.getDocument();
        if (doc == null) {
            return false;
        }
        String title = doc.title();
        if (title == null || !title.contains("ソニー生命保険株式会社")) {
            return false;
        }
        // Basic info page check
        Element premiumElement = doc.getElementById("headform1:id_nxtPtdPr");
        Element frequencyElement = doc.getElementById("headform1:id_mdeQt");
        return premiumElement != null && frequencyElement != null;
    }

    @Override
    public List<InsurancePolicy> parse(InsuranceFilePreview preview) throws Exception {
        Document doc = preview.getDocument();
        List<InsurancePolicy> policies = new ArrayList<>();

        Element policyNoElement = doc.getElementById("id_polNo");
        Element policyNameElement = doc.getElementById("headform1:id_brdIsfNmTop");
        Element premiumElement = doc.getElementById("headform1:id_nxtPtdPr");
        Element frequencyElement = doc.getElementById("headform1:id_mdeQt");

        Element statusElement = doc.getElementById("headform1:id_ist");

        if (policyNoElement == null || policyNameElement == null || premiumElement == null
                || frequencyElement == null) {
            throw new IllegalArgumentException("Missing required elements in Sony Life HTML");
        }

        String policyNo = policyNoElement.text().trim();
        String policyName = policyNameElement.text().trim();
        String premiumStr = premiumElement.text().replaceAll("[^0-9]", "");
        String frequency = frequencyElement.text().trim();
        String status = statusElement != null ? statusElement.text().trim() : "";

        long premium = 0;
        if (!premiumStr.isEmpty()) {
            premium = Long.parseLong(premiumStr);
        }

        long annualPremium = premium;
        if (frequency.contains("一時払") || frequency.contains("払済") || status.contains("払済")) {
            annualPremium = 0L;
        } else if (frequency.contains("月払")) {
            annualPremium *= 12;
        } else if (frequency.contains("半年払")) {
            annualPremium *= 2;
        }

        InsurancePolicyType type = InsurancePolicyType.LIFE;
        if (policyName.contains("がん") || policyName.contains("医療") || policyName.contains("介護")) {
            type = InsurancePolicyType.NURSING_CARE;
        }

        InsurancePolicy policy = new InsurancePolicy(COMPANY_NAME, type, policyNo, policyName, annualPremium, null);

        policies.add(policy);

        return policies;
    }

    @Override
    public String getProviderId() {
        return "sony_life_summary";
    }

    @Override
    public void mergePolicy(InsurancePolicy existing, InsurancePolicy newPolicy) {
        if (existing == null || newPolicy == null) {
            return;
        }

        existing.setCompanyName(newPolicy.getCompanyName()); // Always merge the company name, null is impossible
        existing.setPolicyName(newPolicy.getPolicyName());
        existing.setAnnualPremium(newPolicy.getAnnualPremium());
        if (newPolicy.getEndDate() != null) {
            existing.setEndDate(newPolicy.getEndDate());
        }
    }
}
