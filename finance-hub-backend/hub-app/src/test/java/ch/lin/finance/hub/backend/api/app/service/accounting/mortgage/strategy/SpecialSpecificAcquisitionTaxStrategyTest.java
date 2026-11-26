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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;

class SpecialSpecificAcquisitionTaxStrategyTest {

    private SpecialSpecificAcquisitionTaxStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SpecialSpecificAcquisitionTaxStrategy();
    }

    @Test
    void isApplicable_withinRange_returnsTrue() {
        assertThat(strategy.isApplicable(LocalDate.of(2019, 10, 1))).isTrue();
        assertThat(strategy.isApplicable(LocalDate.of(2020, 5, 15))).isTrue();
        assertThat(strategy.isApplicable(LocalDate.of(2021, 12, 31))).isTrue();
    }

    @Test
    void isApplicable_outsideRange_returnsFalse() {
        assertThat(strategy.isApplicable(null)).isFalse();
        assertThat(strategy.isApplicable(LocalDate.of(2019, 9, 30))).isFalse();
        assertThat(strategy.isApplicable(LocalDate.of(2022, 1, 1))).isFalse();
    }

    @Test
    void calculateDurationYears_newHouse_returns13() {
        assertThat(strategy.calculateDurationYears(PropertyType.NEW_DETACHED_HOUSE)).isEqualTo(13);
        assertThat(strategy.calculateDurationYears(PropertyType.NEW_APARTMENT)).isEqualTo(13);
    }

    @Test
    void calculateDurationYears_usedHouseAndOther_returns10() {
        assertThat(strategy.calculateDurationYears(PropertyType.USED_DETACHED_HOUSE)).isEqualTo(10);
        assertThat(strategy.calculateDurationYears(PropertyType.USED_APARTMENT)).isEqualTo(10);
        assertThat(strategy.calculateDurationYears(PropertyType.OTHER)).isEqualTo(10);
        assertThat(strategy.calculateDurationYears(null)).isEqualTo(10);
    }

    @Test
    void calculateRateAndMaxAmount_returnsCorrectValues() {
        assertThat(strategy.calculateRate(ResidenceType.GENERAL)).isEqualTo(0.01);
        assertThat(strategy.calculateMaxAmount(ResidenceType.GENERAL)).isEqualTo(400_000L);
        assertThat(strategy.calculateMaxAmount(ResidenceType.LONG_TERM_CERTIFIED)).isEqualTo(500_000L);
        assertThat(strategy.calculateMaxAmount(ResidenceType.ZEH)).isEqualTo(500_000L);
        assertThat(strategy.getMaxEligibleTotalIncome()).isEqualTo(30_000_000L);
    }
}
