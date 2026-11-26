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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InhabitantTaxRateStrategyTest {

    private final InhabitantTaxRateStrategyFactory factory = new InhabitantTaxRateStrategyFactory();

    @Nested
    @DisplayName("Standard Strategy (2007+ / Heisei 19 onwards - Flat 10% + 5,000 JPY Flat Levy)")
    class StandardStrategyTests {

        private final InhabitantTaxRateStrategy strategy = new StandardInhabitantTaxRateStrategy();

        @Test
        void shouldHandleNonPositiveTaxableIncome() {
            // When totalIncome <= 450,000 JPY: exempt from flat levy -> 0 JPY
            assertThat(strategy.calculateInhabitantTax(0L, 450_000L)).isEqualTo(0L);
            assertThat(strategy.calculateInhabitantTax(-100_000L, 400_000L)).isEqualTo(0L);

            // When totalIncome > 450,000 JPY: flat levy 5,000 JPY applies even if taxableIncome <= 0
            assertThat(strategy.calculateInhabitantTax(0L, 450_001L)).isEqualTo(5_000L);
            assertThat(strategy.calculateInhabitantTax(0L, 5_000_000L)).isEqualTo(5_000L);
            assertThat(strategy.calculateInhabitantTax(-100_000L, 5_000_000L)).isEqualTo(5_000L);
        }

        @Test
        void shouldCalculateFlat10PercentRatePlusFlatLevy() {
            // totalIncome > 450,000 JPY: 10% income levy + 5,000 JPY flat levy
            assertThat(strategy.calculateInhabitantTax(1_000_000L, 2_000_000L)).isEqualTo(105_000L);
            assertThat(strategy.calculateInhabitantTax(5_000_000L, 7_000_000L)).isEqualTo(505_000L);
            assertThat(strategy.calculateInhabitantTax(10_000_000L, 12_000_000L)).isEqualTo(1_005_000L);
        }

        @Test
        void shouldExemptFlatLevyWhenTotalIncomeIsZeroOrBelowThreshold() {
            // Retirement income separate taxation (passed as 0L totalIncome)
            assertThat(strategy.calculateInhabitantTax(1_000_000L, 0L)).isEqualTo(100_000L);
            assertThat(strategy.calculateInhabitantTax(5_000_000L, 450_000L)).isEqualTo(500_000L);
        }
    }

    @Nested
    @DisplayName("Pre-2007 Strategy (<= 2006 / Pre-Trinity Reform - 3 Brackets 5% ~ 13% + 4,000 JPY Flat Levy)")
    class Pre2007StrategyTests {

        private final InhabitantTaxRateStrategy strategy = new Pre2007InhabitantTaxRateStrategy();

        @Test
        void shouldHandleNonPositiveTaxableIncome() {
            // totalIncome <= 450,000 JPY -> 0 JPY
            assertThat(strategy.calculateInhabitantTax(0L, 450_000L)).isEqualTo(0L);
            assertThat(strategy.calculateInhabitantTax(-100_000L, 300_000L)).isEqualTo(0L);

            // totalIncome > 450,000 JPY -> pre-2007 flat levy 4,000 JPY
            assertThat(strategy.calculateInhabitantTax(0L, 450_001L)).isEqualTo(4_000L);
            assertThat(strategy.calculateInhabitantTax(0L, 2_000_000L)).isEqualTo(4_000L);
        }

        @Test
        void shouldCalculateTier1_under2000k_5PercentPlusFlatLevy() {
            // 1,000,000 * 5% + 4,000 = 54,000
            assertThat(strategy.calculateInhabitantTax(1_000_000L, 2_000_000L)).isEqualTo(54_000L);
            // 2,000,000 * 5% + 4,000 = 104,000
            assertThat(strategy.calculateInhabitantTax(2_000_000L, 3_000_000L)).isEqualTo(104_000L);
        }

        @Test
        void shouldCalculateTier2_2000kTo7000k_10PercentMinus100kPlusFlatLevy() {
            // 5,000,000 * 10% - 100,000 + 4,000 = 404,000
            assertThat(strategy.calculateInhabitantTax(5_000_000L, 6_000_000L)).isEqualTo(404_000L);
            // 7,000,000 * 10% - 100,000 + 4,000 = 604,000
            assertThat(strategy.calculateInhabitantTax(7_000_000L, 8_000_000L)).isEqualTo(604_000L);
        }

        @Test
        void shouldCalculateTier3_above7000k_13PercentMinus310kPlusFlatLevy() {
            // 10,000,000 * 13% - 310,000 + 4,000 = 994,000
            assertThat(strategy.calculateInhabitantTax(10_000_000L, 12_000_000L)).isEqualTo(994_000L);
            // 20,000,000 * 13% - 310,000 + 4,000 = 2,294,000
            assertThat(strategy.calculateInhabitantTax(20_000_000L, 22_000_000L)).isEqualTo(2_294_000L);
        }

        @Test
        void shouldExemptFlatLevyWhenTotalIncomeIsZeroOrBelowThreshold() {
            // Retirement income separate taxation (passed as 0L totalIncome)
            assertThat(strategy.calculateInhabitantTax(1_000_000L, 0L)).isEqualTo(50_000L);
            assertThat(strategy.calculateInhabitantTax(5_000_000L, 450_000L)).isEqualTo(400_000L);
        }
    }

    @Nested
    @DisplayName("Factory Dispatch Tests")
    class FactoryDispatchTests {

        @Test
        void shouldDispatchPre2007StrategyForYear2006AndEarlier() {
            assertThat(factory.getStrategy(2000)).isInstanceOf(Pre2007InhabitantTaxRateStrategy.class);
            assertThat(factory.getStrategy(2006)).isInstanceOf(Pre2007InhabitantTaxRateStrategy.class);
        }

        @Test
        void shouldDispatchStandardStrategyForYear2007AndLater() {
            assertThat(factory.getStrategy(2007)).isInstanceOf(StandardInhabitantTaxRateStrategy.class);
            assertThat(factory.getStrategy(2026)).isInstanceOf(StandardInhabitantTaxRateStrategy.class);
            assertThat(factory.getStrategy(2040)).isInstanceOf(StandardInhabitantTaxRateStrategy.class);
        }
    }
}
