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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsuranceFilePreview;
import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsuranceParser;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;

@Component
public class SonyAssuranceFireHtmlParser implements InsuranceParser {

    public static final String COMPANY_NAME = "ソニー損害保険株式会社";

    private static final Pattern DATE_PATTERN = Pattern
            .compile("(?:（|\\()?(\\d{4})年(?:\\)|）)?\\s*(\\d{1,2})月\\s*(\\d{1,2})日");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)年");

    @Override
    public boolean canParse(InsuranceFilePreview preview) {
        Document doc = preview.getDocument();
        if (doc == null) {
            return false;
        }

        // Basic check for Sony Assurance pages
        String text = doc.text();
        if (!text.contains("SONY ASSURANCE INC.")) {
            return false;
        }

        // Explicitly check for fire insurance keyword to prevent collision with
        // automobile insurance
        if (!text.contains("火災")) {
            return false;
        }

        Element policyNoElement = doc.getElementById("contentsform:lblChdrno");
        return policyNoElement != null;
    }

    @Override
    public List<InsurancePolicy> parse(InsuranceFilePreview preview) throws Exception {
        Document doc = preview.getDocument();
        List<InsurancePolicy> policies = new ArrayList<>();

        Element policyNoElement = doc.getElementById("contentsform:lblChdrno");
        Element premiumElement = doc.getElementById("contentsform:zpremamt");
        Element frequencyElement = doc.getElementById("contentsform:payplan");
        Element startDateElement = doc.getElementById("contentsform:ccdate");
        Element durationElement = doc.getElementById("contentsform:poldurStr");
        if (durationElement == null || durationElement.text().isBlank()) {
            durationElement = doc.getElementById("contentsform:quakeinspriodStr");
        }

        if (policyNoElement == null) {
            throw new IllegalArgumentException("Missing required elements in Sony Assurance HTML");
        }

        String policyNo = policyNoElement.text().trim();
        String premiumStr = premiumElement != null ? premiumElement.text().replaceAll("[^0-9]", "") : "";
        String frequency = frequencyElement != null ? frequencyElement.text().trim() : "";

        long premium = 0;
        if (!premiumStr.isEmpty()) {
            premium = Long.parseLong(premiumStr);
        }

        long annualPremium = premium;
        if (frequency.contains("月払")) {
            annualPremium *= 12;
        } else if (frequency.contains("半年払")) {
            annualPremium *= 2;
        } else if (frequency.contains("長期一括払")) {
            annualPremium = 0; // Handled as 0 for one-time
        }

        LocalDate endDate = parseEndDate(startDateElement, durationElement);

        InsurancePolicyType type = InsurancePolicyType.EARTHQUAKE; // Assuming fire/earthquake insurance
        String policyName = "ソニー損保火災地震保険";

        InsurancePolicy policy = new InsurancePolicy(COMPANY_NAME, type, policyNo, policyName, annualPremium, endDate);

        policies.add(policy);

        return policies;
    }

    private LocalDate parseEndDate(Element startDateElement, Element durationElement) {
        if (startDateElement == null || durationElement == null) {
            return null;
        }

        String startDateText = startDateElement.text().trim();
        String durationText = durationElement.text().trim();

        LocalDate startDate = null;
        Matcher dateMatcher = DATE_PATTERN.matcher(startDateText);
        if (dateMatcher.find()) {
            int year = Integer.parseInt(dateMatcher.group(1));
            int month = Integer.parseInt(dateMatcher.group(2));
            int day = Integer.parseInt(dateMatcher.group(3));
            startDate = LocalDate.of(year, month, day);
        }

        if (startDate == null) {
            return null;
        }

        Matcher durationMatcher = DURATION_PATTERN.matcher(durationText);
        if (durationMatcher.find()) {
            int years = Integer.parseInt(durationMatcher.group(1));
            return startDate.plusYears(years);
        }

        return null;
    }

    @Override
    public String getProviderId() {
        return "sony_assurance_fire";
    }
}
