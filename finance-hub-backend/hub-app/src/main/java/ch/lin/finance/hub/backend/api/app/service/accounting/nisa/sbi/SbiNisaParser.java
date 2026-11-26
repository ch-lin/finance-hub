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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.sbi;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;

/**
 * Interface for parsing SBI NISA portfolio HTML files.
 */
public interface SbiNisaParser {

    /**
     * Parses the SBI HTML file into a list of NISA snapshot records.
     *
     * @param inputStream The input stream of the HTML file.
     * @param recordDate  The date to associate with these snapshot records.
     * @return A list of parsed NISA snapshot records.
     * @throws Exception If parsing fails.
     */
    List<NisaSnapshotRecord> parse(InputStream inputStream, LocalDate recordDate) throws Exception;

    /**
     * Parses the SBI NISA setting HTML file into a list of NISA contribution settings.
     *
     * @param inputStream The input stream of the HTML file.
     * @param recordDate  The date to associate with these records.
     * @return A list of parsed NISA contribution settings.
     * @throws Exception If parsing fails.
     */
    List<NisaContributionSetting> parseSettings(InputStream inputStream, LocalDate recordDate) throws Exception;
}
