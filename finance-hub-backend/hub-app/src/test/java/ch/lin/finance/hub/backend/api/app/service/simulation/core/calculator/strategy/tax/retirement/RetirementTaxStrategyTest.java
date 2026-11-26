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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetirementTaxStrategyTest {

    private final RetirementTaxStrategyFactory factory = new RetirementTaxStrategyFactory();
    private final StandardRetirementTaxStrategy standardStrategy = new StandardRetirementTaxStrategy();
    private final ShortTermRetirementTaxStrategy shortTermStrategy = new ShortTermRetirementTaxStrategy();

    @Nested
    @DisplayName("Strategy Factory Dispatch Tests")
    class FactoryTests {

        @ParameterizedTest(name = "Contribution years {0} should dispatch ShortTermRetirementTaxStrategy")
        @ValueSource(ints = {0, 1, 2, 3, 4, 5})
        void shouldDispatchShortTermStrategyForFiveYearsOrLess(int years) {
            RetirementTaxStrategy strategy = factory.getStrategy(years);
            assertThat(strategy).isInstanceOf(ShortTermRetirementTaxStrategy.class);
        }

        @ParameterizedTest(name = "Contribution years {0} should dispatch StandardRetirementTaxStrategy")
        @ValueSource(ints = {6, 7, 10, 20, 30, 40})
        void shouldDispatchStandardStrategyForMoreThanFiveYears(int years) {
            RetirementTaxStrategy strategy = factory.getStrategy(years);
            assertThat(strategy).isInstanceOf(StandardRetirementTaxStrategy.class);
        }
    }

    @Nested
    @DisplayName("Standard Retirement Tax Strategy Tests (> 5 Years)")
    class StandardStrategyTests {

        @Test
        void shouldReturnZeroWhenIncomeIsLessThanOrEqualToDeduction() {
            assertThat(standardStrategy.calculateTaxableIncome(800_000L, 800_000L)).isZero();
            assertThat(standardStrategy.calculateTaxableIncome(500_000L, 800_000L)).isZero();
            assertThat(standardStrategy.calculateTaxableIncome(0L, 800_000L)).isZero();
        }

        @Test
        void shouldCalculateTaxableIncomeWithHalfTaxation() {
            // Excess = 6,000,000 -> 3,000,000
            assertThat(standardStrategy.calculateTaxableIncome(10_000_000L, 4_000_000L))
                    .isEqualTo(3_000_000L);
        }

        @Test
        void shouldNotCapHalfTaxationWhenExcessExceedsThreeMillion() {
            // Excess = 8,800,000 -> 4,400,000
            assertThat(standardStrategy.calculateTaxableIncome(10_000_000L, 1_200_000L))
                    .isEqualTo(4_400_000L);
        }

        @Test
        void shouldRoundDownToNearestThousandYen() {
            // Excess = 1,500 -> raw = 750 -> 0
            assertThat(standardStrategy.calculateTaxableIncome(1_500L, 0L)).isZero();
            // Excess = 2,500 -> raw = 1,250 -> 1,000
            assertThat(standardStrategy.calculateTaxableIncome(2_500L, 0L)).isEqualTo(1_000L);
            // Excess = 3,999 -> raw = 1,999 -> 1,000
            assertThat(standardStrategy.calculateTaxableIncome(3_999L, 0L)).isEqualTo(1_000L);
        }
    }

    @Nested
    @DisplayName("Short-Term Retirement Tax Strategy Tests (<= 5 Years)")
    class ShortTermStrategyTests {

        @Test
        void shouldReturnZeroWhenIncomeIsLessThanOrEqualToDeduction() {
            assertThat(shortTermStrategy.calculateTaxableIncome(800_000L, 800_000L)).isZero();
            assertThat(shortTermStrategy.calculateTaxableIncome(500_000L, 800_000L)).isZero();
            assertThat(shortTermStrategy.calculateTaxableIncome(0L, 800_000L)).isZero();
        }

        @Test
        void shouldApplyHalfTaxationWhenExcessIsWithinThreeMillion() {
            // Excess = 2,000,000 -> 1,000,000
            assertThat(shortTermStrategy.calculateTaxableIncome(2_800_000L, 800_000L))
                    .isEqualTo(1_000_000L);

            // Excess = exactly 3,000,000 -> 1,500,000
            assertThat(shortTermStrategy.calculateTaxableIncome(3_800_000L, 800_000L))
                    .isEqualTo(1_500_000L);
        }

        @Test
        void shouldApplyAntiAbuseFormulaWhenExcessExceedsThreeMillion() {
            // Excess = 3,001,000 -> 1,500,000 + 1,000 = 1,501,000
            assertThat(shortTermStrategy.calculateTaxableIncome(3_801_000L, 800_000L))
                    .isEqualTo(1_501_000L);

            // Excess = 4,000,000 -> 1,500,000 + 1,000,000 = 2,500,000
            assertThat(shortTermStrategy.calculateTaxableIncome(4_800_000L, 800_000L))
                    .isEqualTo(2_500_000L);

            // Question 122 Reference Case:
            // Retirement income 10,000,000, 3 contribution years (deduction = 1,200,000).
            // Excess = 8,800,000.
            // Statutory taxable income = 1,500,000 + (8,800,000 - 3,000,000) = 7,300,000.
            assertThat(shortTermStrategy.calculateTaxableIncome(10_000_000L, 1_200_000L))
                    .isEqualTo(7_300_000L);
        }

        @Test
        void shouldRoundDownToNearestThousandYen() {
            // Below 3M: Excess = 1,500 -> raw = 750 -> 0
            assertThat(shortTermStrategy.calculateTaxableIncome(1_500L, 0L)).isZero();
            // Below 3M: Excess = 2,500 -> raw = 1,250 -> 1,000
            assertThat(shortTermStrategy.calculateTaxableIncome(2_500L, 0L)).isEqualTo(1_000L);

            // Above 3M: Excess = 3,001,999 -> raw = 1,500,000 + 1,999 = 1,501,999 -> 1,501,000
            assertThat(shortTermStrategy.calculateTaxableIncome(3_001_999L, 0L))
                    .isEqualTo(1_501_000L);
        }
    }
}
