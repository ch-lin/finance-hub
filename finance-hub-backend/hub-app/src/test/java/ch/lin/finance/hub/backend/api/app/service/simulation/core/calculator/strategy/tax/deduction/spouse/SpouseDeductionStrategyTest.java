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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.spouse;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpouseDeductionStrategyTest {

    private final SpouseDeductionStrategyFactory factory = new SpouseDeductionStrategyFactory();

    @Nested
    @DisplayName("Strategy Factory Tests")
    @SuppressWarnings("unused")
    class FactoryTests {

        @Test
        void shouldResolveIncomeTaxStrategy() {
            // Income tax: current year income taxation
            assertThat(factory.getIncomeTaxStrategy(2020)).isInstanceOf(LegacySpouseDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2024)).isInstanceOf(LegacySpouseDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2025)).isInstanceOf(Reiwa7SpouseDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2026)).isInstanceOf(Reiwa8SpouseDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2027)).isInstanceOf(Reiwa8SpouseDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2028)).isInstanceOf(Reiwa10OnwardsSpouseDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2030)).isInstanceOf(Reiwa10OnwardsSpouseDeductionStrategy.class);
        }

        @Test
        void shouldResolveInhabitantTaxStrategy() {
            // Inhabitant tax: prior year income taxation
            // In 2025: assesses 2024 income -> Legacy
            assertThat(factory.getInhabitantTaxStrategy(2024)).isInstanceOf(LegacySpouseDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2025)).isInstanceOf(LegacySpouseDeductionStrategy.class);
            // In 2026: assesses 2025 income -> Reiwa 7!
            assertThat(factory.getInhabitantTaxStrategy(2026)).isInstanceOf(Reiwa7SpouseDeductionStrategy.class);
            // In 2027~2028: assesses 2026~2027 income -> Reiwa 8
            assertThat(factory.getInhabitantTaxStrategy(2027)).isInstanceOf(Reiwa8SpouseDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2028)).isInstanceOf(Reiwa8SpouseDeductionStrategy.class);
            // In 2029+: assesses 2028+ income -> Reiwa 10 Onwards
            assertThat(factory.getInhabitantTaxStrategy(2029)).isInstanceOf(Reiwa10OnwardsSpouseDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2030)).isInstanceOf(Reiwa10OnwardsSpouseDeductionStrategy.class);
        }
    }

    @Nested
    @DisplayName("Legacy Strategy Tests (<= 2024 income)")
    @SuppressWarnings("unused")
    class LegacyStrategyTests {

        private final SpouseDeductionStrategy strategy = new LegacySpouseDeductionStrategy();

        @Test
        void shouldReturnBasicSpouseDeductionUnder103M() {
            // Tier 1: User salary <= 10.95M (income <= 9M)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_000_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_000_000L, true)).isEqualTo(480_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_000_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_000_000L, true)).isEqualTo(380_000L);

            // Tier 2: User salary 11.2M (income 9M ~ 9.5M)
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_000_000L, false)).isEqualTo(260_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_000_000L, true)).isEqualTo(320_000L);

            // Tier 3: User salary 11.8M (income 9.5M ~ 10M)
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_000_000L, false)).isEqualTo(130_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_000_000L, true)).isEqualTo(160_000L);

            // Tier 4: User salary > 11.95M (income > 10M) -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(12_000_000L, 1_000_000L, false)).isZero();
            assertThat(strategy.calculateIncomeTaxDeduction(12_000_000L, 1_000_000L, true)).isZero();
        }

        @Test
        void shouldReturnSpecialSpouseFullDeductionBetween103MAnd150M() {
            // In special deduction, elderly bonus does NOT apply
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_400_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_400_000L, true)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_400_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_400_000L, true)).isEqualTo(330_000L);
        }

        @Test
        void shouldTaperOffBetween150MAnd2016M() {
            // Bracket 1: 1.50M ~ 1.60M -> 360k / 330k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_550_000L, false)).isEqualTo(360_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_550_000L, false)).isEqualTo(330_000L);

            // Bracket 2: 1.60M ~ 1.668M -> 310k / 310k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_650_000L, false)).isEqualTo(310_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_650_000L, false)).isEqualTo(310_000L);

            // Bracket 3: 1.668M ~ 1.752M -> 260k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_700_000L, false)).isEqualTo(260_000L);

            // Bracket 4: 1.752M ~ 1.836M -> 210k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_800_000L, false)).isEqualTo(210_000L);

            // Bracket 5: 1.836M ~ 1.904M -> 160k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_900_000L, false)).isEqualTo(160_000L);

            // Bracket 6: 1.904M ~ 1.972M -> 110k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_950_000L, false)).isEqualTo(110_000L);

            // Bracket 7: 1.972M ~ 2.014M -> 60k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_000_000L, false)).isEqualTo(60_000L);

            // Bracket 8: 2.014M ~ 2.016M -> 30k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_015_000L, false)).isEqualTo(30_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_015_000L, false)).isEqualTo(30_000L);

            // Over 2.016M -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_020_000L, false)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_020_000L, false)).isZero();
        }
    }

    @Nested
    @DisplayName("Reiwa 7 Strategy Tests (2025 income / 2026 inhabitant)")
    @SuppressWarnings("unused")
    class Reiwa7StrategyTests {

        private final SpouseDeductionStrategy strategy = new Reiwa7SpouseDeductionStrategy();

        @Test
        void shouldReturnBasicSpouseDeductionUnder123M() {
            // Spouse salary <= 1.23M (income <= 580k)
            // Tier 1: User salary <= 10.95M (income <= 9M)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_200_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_200_000L, true)).isEqualTo(480_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_200_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_200_000L, true)).isEqualTo(380_000L);

            // Tier 2: User salary 11.2M
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_200_000L, false)).isEqualTo(260_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_200_000L, true)).isEqualTo(320_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(11_200_000L, 1_200_000L, false)).isEqualTo(220_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(11_200_000L, 1_200_000L, true)).isEqualTo(260_000L);

            // Tier 3: User salary 11.8M
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_200_000L, false)).isEqualTo(130_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_200_000L, true)).isEqualTo(160_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(11_800_000L, 1_200_000L, false)).isEqualTo(110_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(11_800_000L, 1_200_000L, true)).isEqualTo(130_000L);

            // Tier 4: User salary > 11.95M -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(12_000_000L, 1_200_000L, false)).isZero();
            assertThat(strategy.calculateIncomeTaxDeduction(12_000_000L, 1_200_000L, true)).isZero();
        }

        @Test
        void shouldReturnSpecialSpouseFullDeductionBetween123MAnd160M() {
            // Special deduction (1.23M ~ 1.60M): no elderly bonus
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_500_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_500_000L, true)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_500_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_500_000L, true)).isEqualTo(330_000L);
        }

        @Test
        void shouldTaperOffBetween160MAnd2016MUnderReiwa7() {
            // Bracket 1: 1.60M ~ 1.65M -> 360k (income tax) / 330k (inhabitant tax maintains 330k)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_620_000L, false)).isEqualTo(360_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_620_000L, false)).isEqualTo(330_000L);

            // Bracket 2: 1.65M ~ 1.70M -> 310k / 310k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_680_000L, false)).isEqualTo(310_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_680_000L, false)).isEqualTo(310_000L);

            // Bracket 3: 1.70M ~ 1.75M -> 260k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_720_000L, false)).isEqualTo(260_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_720_000L, false)).isEqualTo(260_000L);

            // Bracket 4: 1.75M ~ 1.80M -> 210k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_780_000L, false)).isEqualTo(210_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_780_000L, false)).isEqualTo(210_000L);

            // Bracket 5: 1.80M ~ 1.85M -> 160k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_820_000L, false)).isEqualTo(160_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_820_000L, false)).isEqualTo(160_000L);

            // Bracket 6: 1.85M ~ 1.90M -> 110k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_880_000L, false)).isEqualTo(110_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_880_000L, false)).isEqualTo(110_000L);

            // Bracket 7: 1.90M ~ 1.97M -> 60k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_950_000L, false)).isEqualTo(60_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_950_000L, false)).isEqualTo(60_000L);

            // Bracket 8: 1.97M ~ 2.016M -> 30k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_000_000L, false)).isEqualTo(30_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_000_000L, false)).isEqualTo(30_000L);

            // Over 2.016M -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_020_000L, false)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_020_000L, false)).isZero();
        }
    }

    @Nested
    @DisplayName("Reiwa 8 Strategy Tests (>= 2026)")
    @SuppressWarnings("unused")
    class Reiwa8StrategyTests {

        private final SpouseDeductionStrategy strategy = new Reiwa8SpouseDeductionStrategy();

        @Test
        void shouldReturnBasicSpouseDeductionUnder136M() {
            // Spouse salary 1.2M <= 1.36M (income <= 620k)
            // Tier 1: User salary <= 10.95M (income <= 9M)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_200_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_200_000L, true)).isEqualTo(480_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_200_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_200_000L, true)).isEqualTo(380_000L);
            // Verify boundary 1.30M & 1.36M for elderly spouse inhabitant tax deduction
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_300_000L, true)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_360_000L, true)).isEqualTo(380_000L);

            // Tier 2: User salary 11.2M
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_200_000L, false)).isEqualTo(260_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_200_000L, true)).isEqualTo(320_000L);

            // Tier 3: User salary 11.8M
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_200_000L, false)).isEqualTo(130_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_200_000L, true)).isEqualTo(160_000L);

            // Tier 4: User salary > 11.95M -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(12_000_000L, 1_200_000L, false)).isZero();
            assertThat(strategy.calculateIncomeTaxDeduction(12_000_000L, 1_200_000L, true)).isZero();
        }

        @Test
        void shouldReturnSpecialSpouseFullDeductionBetween136MAnd169M() {
            // Key Reiwa 8 feature: Spouse earning 1.50M gets FULL 380,000 deduction (not half-cut!)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_500_000L, false)).isEqualTo(380_000L);
            // In special deduction (> 1.36M), elderly bonus is NOT applicable
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_500_000L, true)).isEqualTo(380_000L);

            // Inhabitant tax: 1.50M is also within full special deduction (330k)
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_500_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_500_000L, true)).isEqualTo(330_000L);

            // Up to 1.69M is full deduction
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_690_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_690_000L, false)).isEqualTo(330_000L);
        }

        @Test
        void shouldTaperOffBetween169MAnd207MUnderReiwa8() {
            // Bracket 1: 1.69M ~ 1.74M -> 360k (income tax) / 330k (inhabitant tax)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_700_000L, false)).isEqualTo(360_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_700_000L, false)).isEqualTo(330_000L);

            // Bracket 2: 1.74M ~ 1.80M -> 310k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_750_000L, false)).isEqualTo(310_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_750_000L, false)).isEqualTo(310_000L);

            // Bracket 3: 1.80M ~ 1.85M -> 260k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_820_000L, false)).isEqualTo(260_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_820_000L, false)).isEqualTo(260_000L);

            // Bracket 4: 1.85M ~ 1.91M -> 210k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_900_000L, false)).isEqualTo(210_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_900_000L, false)).isEqualTo(210_000L);

            // Bracket 5: 1.91M ~ 1.96M -> 160k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_950_000L, false)).isEqualTo(160_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_950_000L, false)).isEqualTo(160_000L);

            // Bracket 6: 1.96M ~ 2.02M -> 110k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_000_000L, false)).isEqualTo(110_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_000_000L, false)).isEqualTo(110_000L);

            // Bracket 7: 2.02M ~ 2.05M -> 60k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_040_000L, false)).isEqualTo(60_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_040_000L, false)).isEqualTo(60_000L);

            // Bracket 8: 2.05M ~ 2.07M -> 30k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_060_000L, false)).isEqualTo(30_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_060_000L, false)).isEqualTo(30_000L);

            // Over 2.07M -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_080_000L, false)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_080_000L, false)).isZero();
        }
    }

    @Nested
    @DisplayName("Reiwa 10 Onwards Strategy Tests (>= 2028 income / >= 2029 inhabitant)")
    @SuppressWarnings("unused")
    class Reiwa10OnwardsStrategyTests {

        private final SpouseDeductionStrategy strategy = new Reiwa10OnwardsSpouseDeductionStrategy();

        @Test
        void shouldReturnBasicSpouseDeductionUnder131M() {
            // Spouse salary <= 1.31M (income <= 620k)
            // Tier 1: User salary <= 10.95M (income <= 9M)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_200_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_200_000L, true)).isEqualTo(480_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_200_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_200_000L, true)).isEqualTo(380_000L);
            // Boundary at 1.31M
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_310_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_310_000L, true)).isEqualTo(480_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_310_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_310_000L, true)).isEqualTo(380_000L);

            // Tier 2: User salary 11.2M
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_200_000L, false)).isEqualTo(260_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_200_000L, 1_200_000L, true)).isEqualTo(320_000L);

            // Tier 3: User salary 11.8M
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_200_000L, false)).isEqualTo(130_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(11_800_000L, 1_200_000L, true)).isEqualTo(160_000L);

            // Tier 4: User salary > 11.95M (high income earners 20M, 35M) -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(12_000_000L, 1_200_000L, false)).isZero();
            assertThat(strategy.calculateIncomeTaxDeduction(20_000_000L, 1_200_000L, true)).isZero();
            assertThat(strategy.calculateIncomeTaxDeduction(35_000_000L, 1_200_000L, false)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(20_000_000L, 1_200_000L, true)).isZero();
        }

        @Test
        void shouldReturnSpecialSpouseFullDeductionBetween131MAnd164M() {
            // Special deduction (1.31M ~ 1.64M): full 380k / 330k, elderly bonus NOT applicable
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_350_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_350_000L, true)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_350_000L, false)).isEqualTo(330_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_350_000L, true)).isEqualTo(330_000L);

            // Boundary at 1.64M
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_640_000L, false)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_640_000L, false)).isEqualTo(330_000L);
        }

        @Test
        void shouldTaperOffBetween164MAnd2016MUnderReiwa10() {
            // Bracket 1: 1.64M ~ 1.69M -> 360k (income tax) / 330k (inhabitant tax)
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_650_000L, false)).isEqualTo(360_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_650_000L, false)).isEqualTo(330_000L);

            // Bracket 2: 1.69M ~ 1.75M -> 310k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_700_000L, false)).isEqualTo(310_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_700_000L, false)).isEqualTo(310_000L);

            // Bracket 3: 1.75M ~ 1.80M -> 260k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_780_000L, false)).isEqualTo(260_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_780_000L, false)).isEqualTo(260_000L);

            // Bracket 4: 1.80M ~ 1.86M -> 210k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_820_000L, false)).isEqualTo(210_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_820_000L, false)).isEqualTo(210_000L);

            // Bracket 5: 1.86M ~ 1.91M -> 160k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_880_000L, false)).isEqualTo(160_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_880_000L, false)).isEqualTo(160_000L);

            // Bracket 6: 1.91M ~ 1.97M -> 110k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_950_000L, false)).isEqualTo(110_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_950_000L, false)).isEqualTo(110_000L);

            // Bracket 7: 1.97M ~ 2.00M -> 60k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 1_990_000L, false)).isEqualTo(60_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 1_990_000L, false)).isEqualTo(60_000L);

            // Bracket 8: 2.00M ~ 2.016M -> 30k
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_010_000L, false)).isEqualTo(30_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_010_000L, false)).isEqualTo(30_000L);

            // Over 2.016M -> 0
            assertThat(strategy.calculateIncomeTaxDeduction(6_000_000L, 2_050_000L, false)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(6_000_000L, 2_050_000L, false)).isZero();
        }
    }
}
