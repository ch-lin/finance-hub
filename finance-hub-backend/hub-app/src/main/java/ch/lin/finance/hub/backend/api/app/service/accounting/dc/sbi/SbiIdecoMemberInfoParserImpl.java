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

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionParser;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionPageType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SbiIdecoMemberInfoParserImpl implements DefinedContributionParser {

    @Override
    public DefinedContributionPageType getPageType() {
        return DefinedContributionPageType.MEMBER_INFO;
    }

    @Override
    public boolean canParse(Document doc) {
        boolean isSbiPage = doc.title().contains("SBI") || doc.text().contains("SBIベネフィット");
        boolean hasEnrollmentDate = doc.selectFirst("th:contains(資格取得年月日), td:contains(資格取得年月日)") != null;
        return isSbiPage && hasEnrollmentDate;
    }

    @Override
    public LocalDate extractRecordDate(Document doc, LocalDate fallbackDate) {
        return fallbackDate;
    }

    @Override
    public DefinedContributionAccountSnapshot parse(Document doc, LocalDate recordDate) {
        DefinedContributionAccountSnapshot snapshot = new DefinedContributionAccountSnapshot(
                recordDate,
                DefinedContributionType.IDECO,
                0L,
                0L,
                0L,
                0L,
                0L);

        Element dateElement = doc.selectFirst("th:contains(資格取得年月日), td:contains(資格取得年月日)");
        if (dateElement != null) {
            String dateText = dateElement.parent().text();
            LocalDate extractedDate = extractDateFromString(dateText);
            if (extractedDate != null) {
                snapshot.setEnrollmentDate(extractedDate);
                log.info("Extracted enrollment date from SBI Member Info: {}", extractedDate);
            } else {
                log.warn("Failed to extract date from string: {}", dateText);
            }
        } else {
            log.warn("Could not find 資格取得年月日 element on the page");
        }

        return snapshot;
    }

    private LocalDate extractDateFromString(String text) {
        Pattern p = Pattern.compile("(\\d{4})[年/\\-]\\s*(\\d{1,2})[月/\\-]\\s*(\\d{1,2})[日]?");
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            } catch (NumberFormatException | java.time.DateTimeException ignored) {
            }
        }
        return null;
    }
}
