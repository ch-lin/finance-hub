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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.utils;

import java.time.LocalDate;
import java.time.Period;

/**
 * Utility class for tax-related date and duration calculations.
 */
public final class TaxDateUtils {

    private TaxDateUtils() {
        // Utility class
    }

    /**
     * Calculates the tax service years (勤続年数) between two dates.
     * 
     * According to Japanese tax law for retirement income deduction,
     * the service duration must include both the start and end dates (inclusive).
     * Any fraction of a year (even a single day) is rounded up to a full year
     * (1年未満の端数は1年に切り上げます).
     *
     * @param startDate The start date of the service (inclusive)
     * @param endDate   The end date of the service (inclusive)
     * @return The calculated service years, rounded up for any partial year
     */
    public static int calculateTaxServiceYears(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }

        if (endDate.isBefore(startDate)) {
            return 0;
        }

        // Period.between computes a half-open interval [startDate, endDate).
        // Since tax years are inclusive, we add 1 day to the end date to make it
        // [startDate, endDate].
        Period p = Period.between(startDate, endDate.plusDays(1));

        int years = p.getYears();

        // If there are any remaining months or days, round up the year.
        if (p.getMonths() > 0 || p.getDays() > 0) {
            years++;
        }

        return years;
    }
}
