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

class Pre2022TaxStrategyTest {

    private Pre2022TaxStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new Pre2022TaxStrategy();
    }

    @Test
    void isApplicable_beforeOct2019_returnsTrue() {
        assertThat(strategy.isApplicable(LocalDate.of(2014, 4, 1))).isTrue();
        assertThat(strategy.isApplicable(LocalDate.of(2019, 9, 30))).isTrue();
    }

    @Test
    void isApplicable_oct2019OrLater_returnsFalse() {
        assertThat(strategy.isApplicable(LocalDate.of(2019, 10, 1))).isFalse();
        assertThat(strategy.isApplicable(LocalDate.of(2021, 1, 1))).isFalse();
    }

    @Test
    void calculateDurationYears_alwaysReturns10() {
        assertThat(strategy.calculateDurationYears(PropertyType.NEW_DETACHED_HOUSE)).isEqualTo(10);
        assertThat(strategy.calculateDurationYears(PropertyType.USED_DETACHED_HOUSE)).isEqualTo(10);
    }

    @Test
    void calculateRateAndMaxAmount_returnsCorrectValues() {
        assertThat(strategy.calculateRate(ResidenceType.GENERAL)).isEqualTo(0.01);
        assertThat(strategy.calculateMaxAmount(ResidenceType.GENERAL)).isEqualTo(400_000L);
        assertThat(strategy.calculateMaxAmount(ResidenceType.LONG_TERM_CERTIFIED)).isEqualTo(500_000L);
        assertThat(strategy.getMaxEligibleTotalIncome()).isEqualTo(30_000_000L);
    }
}
