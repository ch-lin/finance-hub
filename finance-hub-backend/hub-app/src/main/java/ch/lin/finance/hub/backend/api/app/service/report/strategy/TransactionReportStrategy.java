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
package ch.lin.finance.hub.backend.api.app.service.report.strategy;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.app.service.report.ReportDateRange;
import ch.lin.finance.hub.backend.api.app.service.report.ReportSortOption;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Interface defining the contract for generating source-specific financial
 * reports.
 * Implementations of this interface are responsible for querying their
 * respective
 * data repositories and formatting the data into a markdown section.
 */
public interface TransactionReportStrategy {

    /**
     * Returns the transaction source this strategy supports.
     */
    TransactionSource getSupportedSource();

    /**
     * Generates the markdown section for the specific transaction source and
     * calculates the total amount.
     *
     * @param dateRange      The date range to query data for.
     * @param sortOption     The sorting option.
     * @param sectionBuilder The StringBuilder to append the markdown content
     *                       to.
     * @param currentUserId  The ID of the user requesting the report.
     * @return The total amount calculated for this section.
     */
    BigDecimal generateReportSection(ReportDateRange dateRange, ReportSortOption sortOption, String currentUserId,
            StringBuilder sectionBuilder);
}
