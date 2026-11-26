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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc;

import java.time.LocalDate;

import org.jsoup.nodes.Document;

import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionPageType;

/**
 * Interface for parsing Defined Contribution (iDeCo or Corporate DC) HTML
 * files.
 */
public interface DefinedContributionParser {

    /**
     * Checks if this parser can parse the given document.
     */
    boolean canParse(Document document);

    /**
     * Returns the DC page type this parser handles.
     */
    DefinedContributionPageType getPageType();

    /**
     * Parses the DC HTML file into an account snapshot containing fund details.
     *
     * @param document   The parsed HTML document.
     * @param recordDate The date to associate with this snapshot record.
     * @return A parsed DC account snapshot.
     */
    DefinedContributionAccountSnapshot parse(Document document, LocalDate recordDate);

    /**
     * Extracts the record date from the document. Implementations should provide
     * specific extraction logic or return the fallbackDate if the document does not
     * contain a reliable date.
     *
     * @param document     The parsed HTML document.
     * @param fallbackDate The date to use if the document doesn't explicitly state
     *                     one.
     * @return The extracted record date.
     */
    LocalDate extractRecordDate(Document document, LocalDate fallbackDate);
}
