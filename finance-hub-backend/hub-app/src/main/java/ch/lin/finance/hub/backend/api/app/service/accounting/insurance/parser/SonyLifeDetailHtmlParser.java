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

import java.time.LocalDate;
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
public class SonyLifeDetailHtmlParser implements InsuranceParser {

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
        // Must not be summary page (which has headform1:id_nxtPtdPr)
        Element summaryPremium = doc.getElementById("headform1:id_nxtPtdPr");
        if (summaryPremium != null) {
            return false;
        }
        Element detailExpYy = doc.getElementById("headform2:id_isdexpYy");
        Element detailPremium = doc.getElementById("headform2:id_brdPr");
        return detailExpYy != null || detailPremium != null;
    }

    @Override
    public List<InsurancePolicy> parse(InsuranceFilePreview preview) throws Exception {
        Document doc = preview.getDocument();
        List<InsurancePolicy> policies = new ArrayList<>();

        Element policyNoElement = doc.getElementById("id_polNo");
        Element policyNameElement = doc.getElementById("headform2:id_brdIsfNmTop2");
        if (policyNameElement == null) {
            policyNameElement = doc.getElementById("id_brdIsfNm1");
        }

        if (policyNoElement == null || policyNameElement == null) {
            throw new IllegalArgumentException("Missing required elements in Sony Life Detail HTML");
        }

        String policyNo = policyNoElement.text().trim();
        String policyName = policyNameElement.text().trim();

        // Extract expiration / maturity date (保険期間満了日)
        LocalDate endDate = null;
        Element expYy = doc.getElementById("headform2:id_isdexpYy");
        Element expMm = doc.getElementById("headform2:id_isdexpMm");
        Element expDd = doc.getElementById("headform2:id_isdexpDd");
        if (expYy != null && expMm != null && expDd != null) {
            String y = expYy.text().replaceAll("[^0-9]", "");
            String m = expMm.text().replaceAll("[^0-9]", "");
            String d = expDd.text().replaceAll("[^0-9]", "");
            if (!y.isEmpty() && !m.isEmpty() && !d.isEmpty()) {
                endDate = LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
            }
        }

        InsurancePolicyType type = InsurancePolicyType.LIFE;
        if (policyName.contains("がん") || policyName.contains("医療") || policyName.contains("介護")) {
            type = InsurancePolicyType.NURSING_CARE;
        }

        // Detail page doesn't state payment mode (月払/年払), so annualPremium is set to 0L
        // and will be complemented by the summary page during policy merge
        long annualPremium = 0L;

        InsurancePolicy policy = new InsurancePolicy(COMPANY_NAME, type, policyNo, policyName, annualPremium, endDate);
        policies.add(policy);

        return policies;
    }

    @Override
    public String getProviderId() {
        return "sony_life_detail";
    }

    @Override
    public void mergePolicy(InsurancePolicy existing, InsurancePolicy newPolicy) {
        if (existing == null || newPolicy == null) {
            return;
        }

        existing.setCompanyName(newPolicy.getCompanyName()); // Always merge the company name, null is impossible
        existing.setPolicyName(newPolicy.getPolicyName());
        if (newPolicy.getEndDate() != null) {
            existing.setEndDate(newPolicy.getEndDate());
        }
        if (newPolicy.getAnnualPremium() > 0) {
            existing.setAnnualPremium(newPolicy.getAnnualPremium());
        }
    }
}
