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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Command object containing parameters required for generating a financial
 * report.
 * It encapsulates the date boundaries, requested sources, and sorting options.
 */
@Value
@Builder
@Slf4j
public class GenerateReportCommand {

    /**
     * The start date string for the report period (yyyy-MM or yyyy-MM-dd).
     */
    String start;

    /**
     * The end date string for the report period (yyyy-MM or yyyy-MM-dd).
     */
    String end;

    /**
     * The set of report types to include in the report. If null or empty, all
     * sources are included.
     */
    Set<ReportType> types;

    /**
     * The sorting option for the generated report items. Defaults to DATE_DESC.
     */
    @Builder.Default
    ReportSortOption sortOption = ReportSortOption.DATE_DESC;

    /**
     * The ID of the user requesting the report, used for privacy masking.
     */
    String currentUserId;

    /**
     * Constructs a human-readable string describing the report period.
     *
     * @return A descriptive string like "all-time", a specific date, or a range
     *         "start to end".
     */
    public String getReportPeriodString() {
        if (start == null && end == null) {
            return "all-time";
        }

        ReportDateRange range = resolveDateRange();
        if (range.startDate().equals(range.endDate())) {
            return range.startDate().toString();
        }

        String startStr = start != null ? range.startDate().toString() : "all-time";
        String endStr = end != null ? range.endDate().toString() : "now";
        return String.format("%s to %s", startStr, endStr);
    }

    /**
     * Checks whether the start date is chronologically after the end date.
     *
     * @return true if start and end are provided and start is after end.
     */
    private boolean isReversed() {
        return start != null && end != null && !start.isEmpty() && !end.isEmpty() && start.compareTo(end) > 0;
    }

    /**
     * Parses the start date string into a {@link LocalDate}.
     * If the client provided a start date after the end date, they are swapped.
     * If the start date is in yyyy-MM format, it is converted to the first day of
     * that month.
     *
     * @return The parsed start date as a {@link LocalDate}, or null if the start
     *         date is empty.
     */
    private LocalDate getParsedStart() {
        if (isReversed()) {
            log.info("Client provided start date ({}) after end date ({}). Automatically swapping them.", start, end);
        }
        String effectiveStart = isReversed() ? end : start;
        if (effectiveStart == null || effectiveStart.isEmpty())
            return null;
        if (effectiveStart.length() == 7) { // yyyy-MM
            return YearMonth.parse(effectiveStart).atDay(1);
        }
        return LocalDate.parse(effectiveStart); // yyyy-MM-dd
    }

    /**
     * Parses the end date string into a {@link LocalDate}.
     * If the client provided a start date after the end date, they are swapped.
     * If the end date is in yyyy-MM format, it is converted to the last day of that
     * month.
     *
     * @return The parsed end date as a {@link LocalDate}, or null if the end date
     *         is empty.
     */
    private LocalDate getParsedEnd() {
        String effectiveEnd = isReversed() ? start : end;
        if (effectiveEnd == null || effectiveEnd.isEmpty())
            return null;
        if (effectiveEnd.length() == 7) { // yyyy-MM
            return YearMonth.parse(effectiveEnd).atEndOfMonth();
        }
        return LocalDate.parse(effectiveEnd); // yyyy-MM-dd
    }

    /**
     * Resolves the start and end dates into a {@link ReportDateRange}.
     * Handles parsing, missing dates (using defaults), swapped dates, and prevents
     * future dates.
     *
     * @return A valid {@link ReportDateRange}.
     */
    public ReportDateRange resolveDateRange() {
        LocalDate resolvedStart = getParsedStart();
        LocalDate resolvedEnd = getParsedEnd();

        LocalDate startDate = resolvedStart != null ? resolvedStart : LocalDate.of(1900, 1, 1);
        LocalDate endDate = resolvedEnd != null ? resolvedEnd : LocalDate.now();

        LocalDate today = LocalDate.now();

        if (endDate.isAfter(today)) {
            log.info("End date ({}) is in the future. Capping it to today ({}).", endDate, today);
            endDate = today;
        }

        if (startDate.isAfter(today)) {
            log.info("Start date ({}) is in the future. Capping it to today ({}).", startDate, today);
            startDate = today;
        }

        return new ReportDateRange(startDate, endDate);
    }
}
