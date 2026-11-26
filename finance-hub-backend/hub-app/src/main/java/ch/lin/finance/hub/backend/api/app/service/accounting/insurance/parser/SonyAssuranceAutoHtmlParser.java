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
public class SonyAssuranceAutoHtmlParser implements InsuranceParser {

    public static final String COMPANY_NAME = "ソニー損害保険株式会社";

    private static final Pattern POLICY_NO_PATTERN = Pattern.compile("証券番号[：:]\\s*([0-9A-Za-z]+)");
    private static final Pattern DATE_PATTERN = Pattern
            .compile("(?:（|\\()?(\\d{4})年(?:\\)|）)?\\s*(\\d{1,2})月\\s*(\\d{1,2})日");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)年");

    @Override
    public boolean canParse(InsuranceFilePreview preview) {
        Document doc = preview.getDocument();
        if (doc == null) {
            return false;
        }

        // Must be Sony Assurance
        String text = doc.text();
        if (!text.contains("SONY ASSURANCE INC.")) {
            return false;
        }

        // Must be Automobile insurance and not Fire insurance
        if (!text.contains("自動車") || text.contains("火災")) {
            return false;
        }

        // Automobile insurance specific elements
        return doc.getElementById("contentsform:lblBpltnoe") != null
                || doc.getElementById("contentsform:lblTotpre") != null
                || !doc.select("body.PPV01010").isEmpty();
    }

    @Override
    public List<InsurancePolicy> parse(InsuranceFilePreview preview) throws Exception {
        Document doc = preview.getDocument();
        List<InsurancePolicy> policies = new ArrayList<>();

        String policyNo = extractPolicyNo(doc);
        if (policyNo == null || policyNo.isBlank()) {
            throw new IllegalArgumentException("Missing required policy number in Sony Assurance Automobile HTML");
        }

        Element premiumElement = doc.getElementById("contentsform:lblTotpre");
        long annualPremium = parsePremium(premiumElement);

        Element durationElement = doc.getElementById("contentsform:lblPoldur");
        if (durationElement == null) {
            durationElement = doc.getElementById("contentsform:lblPoldur2");
        }
        LocalDate endDate = parseEndDate(durationElement);

        String baseName = "ソニー損保総合自動車保険";
        InsurancePolicy policy = new InsurancePolicy(
                COMPANY_NAME,
                InsurancePolicyType.AUTO,
                policyNo,
                baseName,
                annualPremium,
                endDate);

        policies.add(policy);
        return policies;
    }

    private String extractPolicyNo(Document doc) {
        // 1. Try regex from page text (e.g., "（証券番号：9L924089）")
        Matcher matcher = POLICY_NO_PATTERN.matcher(doc.text());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 2. Try looking in table with header "証券番号"
        for (Element th : doc.select("th")) {
            if (th.text().contains("証券番号")) {
                Element td = th.nextElementSibling();
                if (td != null && !td.text().isBlank()) {
                    return td.text().trim();
                }
            }
        }

        return null;
    }

    private long parsePremium(Element premiumElement) {
        if (premiumElement == null) {
            return 0L;
        }
        String text = premiumElement.text().replaceAll("[^0-9]", "");
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private LocalDate parseEndDate(Element durationElement) {
        if (durationElement == null) {
            return null;
        }

        String text = durationElement.text();
        Matcher matcher = DATE_PATTERN.matcher(text);

        LocalDate startDate = null;
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            startDate = LocalDate.of(year, month, day);
        }

        // Second date occurrence in period string is the end date (満期日)
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day);
        }

        // Fallback: calculate using duration if only start date was present
        if (startDate != null) {
            Matcher durationMatcher = DURATION_PATTERN.matcher(text);
            if (durationMatcher.find()) {
                int years = Integer.parseInt(durationMatcher.group(1));
                return startDate.plusYears(years);
            }
        }

        return null;
    }

    @Override
    public String getProviderId() {
        return "sony_assurance_auto";
    }
}
