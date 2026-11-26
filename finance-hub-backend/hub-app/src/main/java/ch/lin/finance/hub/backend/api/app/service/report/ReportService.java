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
package ch.lin.finance.hub.backend.api.app.service.report;

/**
 * Service responsible for generating financial summary reports.
 */
public interface ReportService {

    /**
     * Generates a high-level Markdown summary report for a given time period.
     *
     * @param command The command containing the reporting parameters (month,
     * type, sort option).
     * @return The formatted Markdown string.
     */
    String generateSummaryReport(GenerateReportCommand command);
}
