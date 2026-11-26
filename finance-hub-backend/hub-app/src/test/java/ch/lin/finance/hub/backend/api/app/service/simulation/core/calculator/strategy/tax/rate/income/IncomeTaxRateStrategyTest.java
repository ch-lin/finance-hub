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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IncomeTaxRateStrategyTest {

    private final IncomeTaxRateStrategyFactory factory = new IncomeTaxRateStrategyFactory();

    @Nested
    @DisplayName("Standard Strategy (2015+ / Heisei 27 onwards - 7 Brackets)")
    class StandardStrategyTests {

        private final IncomeTaxRateStrategy strategy = new StandardIncomeTaxRateStrategy();

        @Test
        void shouldHandleNonPositiveTaxableIncome() {
            assertThat(strategy.calculateIncomeTax(0L)).isEqualTo(0L);
            assertThat(strategy.calculateIncomeTax(-100_000L)).isEqualTo(0L);
        }

        @Test
        void shouldCalculateTier1_under1950k() {
            assertThat(strategy.calculateIncomeTax(1_000_000L)).isEqualTo(50_000L);
            assertThat(strategy.calculateIncomeTax(1_950_000L)).isEqualTo(97_500L);
        }

        @Test
        void shouldCalculateTier2_1950kTo3300k() {
            // 2,000,000 * 10% - 97,500 = 102,500
            assertThat(strategy.calculateIncomeTax(2_000_000L)).isEqualTo(102_500L);
            // 3,300,000 * 10% - 97,500 = 232,500
            assertThat(strategy.calculateIncomeTax(3_300_000L)).isEqualTo(232_500L);
        }

        @Test
        void shouldCalculateTier3_3300kTo6950k() {
            // 5,000,000 * 20% - 427,500 = 572,500
            assertThat(strategy.calculateIncomeTax(5_000_000L)).isEqualTo(572_500L);
            // 6,950,000 * 20% - 427,500 = 962,500
            assertThat(strategy.calculateIncomeTax(6_950_000L)).isEqualTo(962_500L);
        }

        @Test
        void shouldCalculateTier4_6950kTo9000k() {
            // 8,000,000 * 23% - 636,000 = 1,204,000
            assertThat(strategy.calculateIncomeTax(8_000_000L)).isEqualTo(1_204_000L);
            // 9,000,000 * 23% - 636,000 = 1,434,000
            assertThat(strategy.calculateIncomeTax(9_000_000L)).isEqualTo(1_434_000L);
        }

        @Test
        void shouldCalculateTier5_9000kTo18000k() {
            // 10,000,000 * 33% - 1,536,000 = 1,764,000
            assertThat(strategy.calculateIncomeTax(10_000_000L)).isEqualTo(1_764_000L);
            // 18,000,000 * 33% - 1,536,000 = 4,404,000
            assertThat(strategy.calculateIncomeTax(18_000_000L)).isEqualTo(4_404_000L);
        }

        @Test
        void shouldCalculateTier6_18000kTo40000k() {
            // 20,000,000 * 40% - 2,796,000 = 5,204,000
            assertThat(strategy.calculateIncomeTax(20_000_000L)).isEqualTo(5_204_000L);
            // 40,000,000 * 40% - 2,796,000 = 13,204,000
            assertThat(strategy.calculateIncomeTax(40_000_000L)).isEqualTo(13_204_000L);
        }

        @Test
        void shouldCalculateTier7_above40000k() {
            // 50,000,000 * 45% - 4,796,000 = 17,704,000
            assertThat(strategy.calculateIncomeTax(50_000_000L)).isEqualTo(17_704_000L);
        }
    }

    @Nested
    @DisplayName("Pre-2015 Strategy (<= 2014 / Pre-Heisei 27 - 6 Brackets capped at 40%)")
    class Pre2015StrategyTests {

        private final IncomeTaxRateStrategy strategy = new Pre2015IncomeTaxRateStrategy();

        @Test
        void shouldHandleNonPositiveTaxableIncome() {
            assertThat(strategy.calculateIncomeTax(0L)).isEqualTo(0L);
            assertThat(strategy.calculateIncomeTax(-100_000L)).isEqualTo(0L);
        }

        @Test
        void shouldMatchStandardTiersUpTo18000k() {
            assertThat(strategy.calculateIncomeTax(10_000_000L)).isEqualTo(1_764_000L);
            assertThat(strategy.calculateIncomeTax(18_000_000L)).isEqualTo(4_404_000L);
        }

        @Test
        void shouldCapAt40PercentAbove18000kWithout45PercentTier() {
            // In pre-2015: 50,000,000 * 40% - 2,796,000 = 17,204,000
            // Notice: standard 2015+ returns 17,704,000 (500,000 difference due to 45% tier)
            assertThat(strategy.calculateIncomeTax(50_000_000L)).isEqualTo(17_204_000L);
        }
    }

    @Nested
    @DisplayName("Factory Dispatch Tests")
    class FactoryDispatchTests {

        @Test
        void shouldDispatchPre2015StrategyForYear2014AndEarlier() {
            assertThat(factory.getStrategy(2010)).isInstanceOf(Pre2015IncomeTaxRateStrategy.class);
            assertThat(factory.getStrategy(2014)).isInstanceOf(Pre2015IncomeTaxRateStrategy.class);
        }

        @Test
        void shouldDispatchStandardStrategyForYear2015AndLater() {
            assertThat(factory.getStrategy(2015)).isInstanceOf(StandardIncomeTaxRateStrategy.class);
            assertThat(factory.getStrategy(2026)).isInstanceOf(StandardIncomeTaxRateStrategy.class);
            assertThat(factory.getStrategy(2040)).isInstanceOf(StandardIncomeTaxRateStrategy.class);
        }
    }
}
