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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EmploymentIncomeDeductionStrategyTest {

    private final EmploymentIncomeDeductionStrategyFactory factory = new EmploymentIncomeDeductionStrategyFactory();

    @Nested
    @DisplayName("Legacy Strategy (<= 2024)")
    class LegacyStrategyTests {

        private final EmploymentIncomeDeductionStrategy strategy = new LegacyEmploymentIncomeDeductionStrategy();

        @Test
        void shouldHandleNonPositiveSalary() {
            assertThat(strategy.calculateDeduction(0L)).isEqualTo(0L);
            assertThat(strategy.calculateDeduction(-100_000L)).isEqualTo(0L);
        }

        @Test
        void shouldCalculateStatutoryTiers() {
            // Tier 1: <= 1.625M -> 550,000 JPY
            assertThat(strategy.calculateDeduction(1_500_000L)).isEqualTo(550_000L);
            assertThat(strategy.calculateDeduction(1_625_000L)).isEqualTo(550_000L);

            // Tier 2: 1.625M ~ 1.8M -> S * 40% - 100,000
            assertThat(strategy.calculateDeduction(1_700_000L)).isEqualTo(580_000L);
            assertThat(strategy.calculateDeduction(1_800_000L)).isEqualTo(620_000L);

            // Tier 3: 1.8M ~ 3.6M -> S * 30% + 80,000
            assertThat(strategy.calculateDeduction(3_000_000L)).isEqualTo(980_000L);
            assertThat(strategy.calculateDeduction(3_600_000L)).isEqualTo(1_160_000L);

            // Tier 4: 3.6M ~ 6.6M -> S * 20% + 440,000
            assertThat(strategy.calculateDeduction(5_000_000L)).isEqualTo(1_440_000L);
            assertThat(strategy.calculateDeduction(6_600_000L)).isEqualTo(1_760_000L);

            // Tier 5: 6.6M ~ 8.5M -> S * 10% + 1,100,000
            assertThat(strategy.calculateDeduction(8_000_000L)).isEqualTo(1_900_000L);
            assertThat(strategy.calculateDeduction(8_500_000L)).isEqualTo(1_950_000L);

            // Cap: > 8.5M -> 1,950,000 JPY
            assertThat(strategy.calculateDeduction(10_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(20_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(35_000_000L)).isEqualTo(1_950_000L);
        }
    }

    @Nested
    @DisplayName("Reiwa 7 Strategy (2025)")
    class Reiwa7StrategyTests {

        private final EmploymentIncomeDeductionStrategy strategy = new Reiwa7EmploymentIncomeDeductionStrategy();

        @Test
        void shouldHandleNonPositiveSalary() {
            assertThat(strategy.calculateDeduction(0L)).isEqualTo(0L);
            assertThat(strategy.calculateDeduction(-50_000L)).isEqualTo(0L);
        }

        @Test
        void shouldCalculateStatutoryTiers() {
            // Tier 1: <= 1.9M -> 650,000 JPY
            assertThat(strategy.calculateDeduction(1_800_000L)).isEqualTo(650_000L);
            assertThat(strategy.calculateDeduction(1_900_000L)).isEqualTo(650_000L);

            // Tier 2: 1.9M ~ 3.6M -> S * 30% + 80,000 (smooth connection at 1.9M: 1.9M * 0.3 + 80k = 650k)
            assertThat(strategy.calculateDeduction(2_000_000L)).isEqualTo(680_000L);
            assertThat(strategy.calculateDeduction(3_600_000L)).isEqualTo(1_160_000L);

            // Tier 3: 3.6M ~ 6.6M -> S * 20% + 440,000
            assertThat(strategy.calculateDeduction(5_000_000L)).isEqualTo(1_440_000L);

            // Cap & High Income
            assertThat(strategy.calculateDeduction(10_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(20_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(35_000_000L)).isEqualTo(1_950_000L);
        }
    }

    @Nested
    @DisplayName("Reiwa 8 and 9 Strategy (2026-2027)")
    class Reiwa8And9StrategyTests {

        private final EmploymentIncomeDeductionStrategy strategy = new Reiwa8And9EmploymentIncomeDeductionStrategy();

        @Test
        void shouldHandleNonPositiveSalary() {
            assertThat(strategy.calculateDeduction(0L)).isEqualTo(0L);
        }

        @Test
        void shouldCalculateStatutoryTiers() {
            // Special exception Tier 1: <= 2.2M -> 740,000 JPY
            assertThat(strategy.calculateDeduction(2_000_000L)).isEqualTo(740_000L);
            assertThat(strategy.calculateDeduction(2_200_000L)).isEqualTo(740_000L);

            // Tier 2: 2.2M ~ 3.6M -> S * 30% + 80,000 (smooth connection: 2.2M * 0.3 + 80k = 740k)
            assertThat(strategy.calculateDeduction(3_000_000L)).isEqualTo(980_000L);
            assertThat(strategy.calculateDeduction(3_600_000L)).isEqualTo(1_160_000L);

            // Tier 3: 3.6M ~ 6.6M -> S * 20% + 440,000
            assertThat(strategy.calculateDeduction(5_000_000L)).isEqualTo(1_440_000L);

            // Cap & High Income
            assertThat(strategy.calculateDeduction(10_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(20_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(35_000_000L)).isEqualTo(1_950_000L);
        }
    }

    @Nested
    @DisplayName("Reiwa 10 Onwards Strategy (>= 2028)")
    class Reiwa10OnwardsStrategyTests {

        private final EmploymentIncomeDeductionStrategy strategy = new Reiwa10OnwardsEmploymentIncomeDeductionStrategy();

        @Test
        void shouldHandleNonPositiveSalary() {
            assertThat(strategy.calculateDeduction(0L)).isEqualTo(0L);
        }

        @Test
        void shouldGuaranteeMinimum690kAndSmoothTransition() {
            // Lowest bracket: guarantees 690,000 JPY
            assertThat(strategy.calculateDeduction(1_800_000L)).isEqualTo(690_000L);
            // 2.0M: 2M * 0.3 + 80k = 680k < 690k -> guarantees 690k
            assertThat(strategy.calculateDeduction(2_000_000L)).isEqualTo(690_000L);
            // Transition threshold: 610,000 / 0.3 = 2,033,333.33 JPY
            assertThat(strategy.calculateDeduction(2_033_333L)).isEqualTo(690_000L);
            assertThat(strategy.calculateDeduction(2_033_334L)).isEqualTo(690_000L);

            // Smoothly increases above 2,033,334
            // 2.1M: 2.1M * 0.3 + 80k = 710k
            assertThat(strategy.calculateDeduction(2_100_000L)).isEqualTo(710_000L);
            // 2.2M: 2.2M * 0.3 + 80k = 740k
            assertThat(strategy.calculateDeduction(2_200_000L)).isEqualTo(740_000L);

            // Middle tiers
            assertThat(strategy.calculateDeduction(3_000_000L)).isEqualTo(980_000L);
            assertThat(strategy.calculateDeduction(5_000_000L)).isEqualTo(1_440_000L);

            // Cap & High Income
            assertThat(strategy.calculateDeduction(10_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(20_000_000L)).isEqualTo(1_950_000L);
            assertThat(strategy.calculateDeduction(35_000_000L)).isEqualTo(1_950_000L);
        }
    }

    @Nested
    @DisplayName("Strategy Factory Routing")
    class FactoryRoutingTests {

        @Test
        void shouldRouteToCorrectStrategyPerYear() {
            assertThat(factory.getStrategy(2020)).isInstanceOf(LegacyEmploymentIncomeDeductionStrategy.class);
            assertThat(factory.getStrategy(2024)).isInstanceOf(LegacyEmploymentIncomeDeductionStrategy.class);
            assertThat(factory.getStrategy(2025)).isInstanceOf(Reiwa7EmploymentIncomeDeductionStrategy.class);
            assertThat(factory.getStrategy(2026)).isInstanceOf(Reiwa8And9EmploymentIncomeDeductionStrategy.class);
            assertThat(factory.getStrategy(2027)).isInstanceOf(Reiwa8And9EmploymentIncomeDeductionStrategy.class);
            assertThat(factory.getStrategy(2028)).isInstanceOf(Reiwa10OnwardsEmploymentIncomeDeductionStrategy.class);
            assertThat(factory.getStrategy(2035)).isInstanceOf(Reiwa10OnwardsEmploymentIncomeDeductionStrategy.class);
            assertThat(factory.getStrategy(2060)).isInstanceOf(Reiwa10OnwardsEmploymentIncomeDeductionStrategy.class);
        }
    }
}
