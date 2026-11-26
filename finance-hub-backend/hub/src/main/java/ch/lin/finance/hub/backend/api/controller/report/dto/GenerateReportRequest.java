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
package ch.lin.finance.hub.backend.api.controller.report.dto;

import java.util.Set;

import ch.lin.finance.hub.backend.api.app.service.report.ReportSortOption;
import ch.lin.finance.hub.backend.api.app.service.report.ReportType;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Data Transfer Object representing a request to generate a financial report.
 */
@Data
@Slf4j
public class GenerateReportRequest {

    /**
     * The start date for the report, in yyyy-MM or yyyy-MM-dd format.
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}(-\\d{2})?$", message = "Start date must be in yyyy-MM or yyyy-MM-dd format")
    private String start;

    /**
     * The end date for the report, in yyyy-MM or yyyy-MM-dd format.
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}(-\\d{2})?$", message = "End date must be in yyyy-MM or yyyy-MM-dd format")
    private String end;

    // --- Common Parameters ---
    /**
     * The set of report types to include (e.g., AMAZON, CREDIT_CARD).
     */
    private Set<ReportType> types;

    /**
     * The option used to sort the items within the report.
     */
    private ReportSortOption sortOption = ReportSortOption.DATE_DESC;

}
