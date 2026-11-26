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
package ch.lin.finance.hub.backend.api.domain.model.accounting.nisa;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class NisaContributionSettingTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        NisaContributionSetting setting = new NisaContributionSetting(
                date,
                "NISA (成長)預り",
                "03311187",
                "eMAXIS Slim S&P 500",
                "毎月",
                50_000L);

        assertEquals(date, setting.getRecordDate());
        assertEquals("NISA (成長)預り", setting.getAccountType());
        assertEquals("03311187", setting.getFundCode());
        assertEquals("eMAXIS Slim S&P 500", setting.getFundName());
        assertEquals("毎月", setting.getCourse());
        assertEquals(50_000L, setting.getSettingAmount());
        assertNull(setting.getExpectedReturnRate());

        setting.updateExpectedReturnRate(0.06);
        assertEquals(0.06, setting.getExpectedReturnRate());
    }

    @Test
    void testConstructorWithNulls() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        NisaContributionSetting setting = new NisaContributionSetting(
                date,
                "NISA (成長)預り",
                null,
                "eMAXIS Slim S&P 500",
                "毎月",
                null);

        assertNull(setting.getFundCode());
        assertEquals(0L, setting.getSettingAmount());
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDate date = LocalDate.of(2025, 1, 1);

        NisaContributionSetting s1 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎月", 1000L);
        NisaContributionSetting s2 = new NisaContributionSetting(date, "NISA", "03311187", "FundA Renamed", "毎月",
                2000L);
        NisaContributionSetting s3 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎日", 1000L);
        NisaContributionSetting s4 = new NisaContributionSetting(date, "NISA", "0331418A", "FundA", "毎月", 1000L);

        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
        assertNotEquals(s1, s4);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1.hashCode(), s3.hashCode());
        assertNotEquals(s1.hashCode(), s4.hashCode());
    }

    @Test
    void testMerge_OverwritesFactsAndPreservesExpectedReturnRate() {
        LocalDate date1 = LocalDate.of(2025, 1, 1);
        NisaContributionSetting existing = new NisaContributionSetting(
                date1, "NISA (成長)預り", "03311187", "eMAXIS Slim S&P 500", "毎月", 50_000L);
        existing.updateExpectedReturnRate(0.06);

        LocalDate date2 = LocalDate.of(2025, 1, 2);
        NisaContributionSetting incoming = new NisaContributionSetting(
                date2, "NISA (成長)預り", "03311188", "eMAXIS Slim All Country", "毎日", 3_000L);

        existing.merge(incoming);

        // Factual fields overwritten
        assertEquals(date2, existing.getRecordDate());
        assertEquals("NISA (成長)預り", existing.getAccountType());
        assertEquals("03311188", existing.getFundCode());
        assertEquals("eMAXIS Slim All Country", existing.getFundName());
        assertEquals("毎日", existing.getCourse());
        assertEquals(3_000L, existing.getSettingAmount());

        // User-defined expectedReturnRate strictly preserved
        assertEquals(0.06, existing.getExpectedReturnRate());

        // Null safe
        existing.merge(null);
        assertEquals(3_000L, existing.getSettingAmount());
        assertEquals(0.06, existing.getExpectedReturnRate());
    }
}
