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

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TaxDateUtilsTest {

    @Test
    void testCalculateTaxServiceYears_ExactYear() {
        // 1 year exactly: 2020-01-01 to 2020-12-31 inclusive
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 12, 31);
        assertEquals(1, TaxDateUtils.calculateTaxServiceYears(start, end));
    }

    @Test
    void testCalculateTaxServiceYears_LeapYearExactYear() {
        // 1 year exactly (leap year): 2020-02-01 to 2021-01-31
        LocalDate start = LocalDate.of(2020, 2, 1);
        LocalDate end = LocalDate.of(2021, 1, 31);
        assertEquals(1, TaxDateUtils.calculateTaxServiceYears(start, end));
    }

    @Test
    void testCalculateTaxServiceYears_OneYearAndOneDay() {
        // 1 year and 1 day: 2020-01-01 to 2021-01-01 inclusive
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2021, 1, 1);
        assertEquals(2, TaxDateUtils.calculateTaxServiceYears(start, end));
    }

    @Test
    void testCalculateTaxServiceYears_OneDayOnly() {
        // Just 1 day of service
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 1, 1);
        assertEquals(1, TaxDateUtils.calculateTaxServiceYears(start, end));
    }

    @Test
    void testCalculateTaxServiceYears_LessThanOneYear() {
        // 11 months and 30 days
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 12, 30);
        assertEquals(1, TaxDateUtils.calculateTaxServiceYears(start, end));
    }

    @Test
    void testCalculateTaxServiceYears_NullInputs() {
        assertEquals(0, TaxDateUtils.calculateTaxServiceYears(null, LocalDate.now()));
        assertEquals(0, TaxDateUtils.calculateTaxServiceYears(LocalDate.now(), null));
        assertEquals(0, TaxDateUtils.calculateTaxServiceYears(null, null));
    }

    @Test
    void testCalculateTaxServiceYears_EndDateBeforeStartDate() {
        LocalDate start = LocalDate.of(2020, 1, 2);
        LocalDate end = LocalDate.of(2020, 1, 1);
        assertEquals(0, TaxDateUtils.calculateTaxServiceYears(start, end));
    }

    @Test
    void testCalculateTaxServiceYears_AnniversaryEdgeCase() {
        // User joined on 12-31, and we calculate up to exactly 6 years later on 12-31.
        // It covers 6 calendar years and 1 extra day (both Dec 31sts).
        LocalDate start = LocalDate.of(2020, 12, 31);
        LocalDate end = LocalDate.of(2026, 12, 31);
        assertEquals(7, TaxDateUtils.calculateTaxServiceYears(start, end));
    }
}
