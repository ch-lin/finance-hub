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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationFundTest {

    @Test
    @DisplayName("calculateContribution should return annualContribution when not initial year")
    void shouldReturnAnnualContributionWhenNotInitialYear() {
        SimulationFund fund = new SimulationFund(
                1L,
                "Test Fund",
                0L,
                520_000L,
                null,
                null,
                "毎週",
                10_000L);

        long result = fund.calculateContribution(false, 3);
        assertThat(result).isEqualTo(520_000L);
    }

    @Test
    @DisplayName("calculateContribution should calculate weekly proportional contribution for initial transition year")
    void shouldCalculateWeeklyProportionalContributionForInitialYear() {
        SimulationFund fund = new SimulationFund(
                1L,
                "Test Fund",
                0L,
                520_000L,
                null,
                null,
                "毎週",
                10_000L);

        // 12 months -> 52 weeks: 10,000 * 52 = 520,000
        assertThat(fund.calculateContribution(true, 12)).isEqualTo(520_000L);

        // 6 months -> 26 weeks: 10,000 * 26 = 260,000
        assertThat(fund.calculateContribution(true, 6)).isEqualTo(260_000L);

        // 3 months -> 13 weeks: 10,000 * 13 = 130,000
        assertThat(fund.calculateContribution(true, 3)).isEqualTo(130_000L);

        // 1 month -> 4 weeks: 10,000 * 4 = 40,000
        assertThat(fund.calculateContribution(true, 1)).isEqualTo(40_000L);

        // 2 months -> 9 weeks (2 * 52 / 12 = 8.666... -> 9): 10,000 * 9 = 90,000
        assertThat(fund.calculateContribution(true, 2)).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("calculateContribution should calculate daily contribution for initial transition year")
    void shouldCalculateDailyContributionForInitialYear() {
        SimulationFund fund = new SimulationFund(
                1L,
                "Test Fund",
                0L,
                240_000L,
                null,
                null,
                "毎日",
                1_000L);

        // 3 months -> 3 * 20 = 60 days: 1,000 * 60 = 60,000
        assertThat(fund.calculateContribution(true, 3)).isEqualTo(60_000L);

        // 12 months -> 12 * 20 = 240 days: 1,000 * 240 = 240,000
        assertThat(fund.calculateContribution(true, 12)).isEqualTo(240_000L);
    }

    @Test
    @DisplayName("calculateContribution should calculate monthly contribution for initial transition year")
    void shouldCalculateMonthlyContributionForInitialYear() {
        SimulationFund fund = new SimulationFund(
                1L,
                "Test Fund",
                0L,
                600_000L,
                null,
                null,
                "毎月",
                50_000L);

        assertThat(fund.calculateContribution(true, 3)).isEqualTo(150_000L);
        assertThat(fund.calculateContribution(true, 12)).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("calculateContribution should return 0 for initial year when settingAmount is zero")
    void shouldReturnZeroForInitialYearWhenSettingAmountIsZero() {
        SimulationFund fund = new SimulationFund(
                1L,
                "Test Fund",
                0L,
                960_000L,
                null,
                null,
                null,
                0L);

        assertThat(fund.calculateContribution(true, 5)).isEqualTo(0L);
        assertThat(fund.calculateContribution(false, 5)).isEqualTo(960_000L);
    }


    @Test
    @DisplayName("course helper methods should identify course variations correctly")
    void shouldIdentifyCourseVariationsCorrectly() {
        assertThat(SimulationFund.isWeekly("毎週")).isTrue();
        assertThat(SimulationFund.isWeekly("weekly")).isTrue();
        assertThat(SimulationFund.isWeekly("every week")).isTrue();
        assertThat(SimulationFund.isWeekly("毎月")).isFalse();

        assertThat(SimulationFund.isDaily("毎日")).isTrue();
        assertThat(SimulationFund.isDaily("daily")).isTrue();
        assertThat(SimulationFund.isDaily("every day")).isTrue();
        assertThat(SimulationFund.isDaily("毎週")).isFalse();

        assertThat(SimulationFund.isMonthly("毎月")).isTrue();
        assertThat(SimulationFund.isMonthly("monthly")).isTrue();
        assertThat(SimulationFund.isMonthly("every month")).isTrue();
        assertThat(SimulationFund.isMonthly("毎日")).isFalse();
    }

    @Test
    @DisplayName("clone should preserve course and settingAmount")
    void shouldCloneAllFieldsIncludingCourseAndSettingAmount() {
        SimulationFund fund = new SimulationFund(
                10L,
                "eMAXIS Slim All Country",
                1_000_000L,
                520_000L,
                0.05,
                NisaAccountType.TSUMITATE,
                "毎週",
                10_000L);

        SimulationFund cloned = fund.clone();
        assertThat(cloned).isNotSameAs(fund);
        assertThat(cloned.getCourse()).isEqualTo("毎週");
        assertThat(cloned.getSettingAmount()).isEqualTo(10_000L);
        assertThat(cloned.getAnnualContribution()).isEqualTo(520_000L);
        assertThat(cloned.getNisaAccountType()).isEqualTo(NisaAccountType.TSUMITATE);
    }
}
