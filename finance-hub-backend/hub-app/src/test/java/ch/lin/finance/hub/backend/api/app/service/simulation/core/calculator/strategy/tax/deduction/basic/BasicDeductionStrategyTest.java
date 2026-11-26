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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BasicDeductionStrategyTest {

    private final BasicDeductionStrategyFactory factory = new BasicDeductionStrategyFactory();

    @Nested
    @DisplayName("Legacy Strategy (<= 2024)")
    @SuppressWarnings("unused")
    class LegacyStrategyTests {

        private final BasicDeductionStrategy strategy = new LegacyBasicDeductionStrategy();

        @Test
        void shouldReturnStandardDeductionBelow24M() {
            assertThat(strategy.calculateIncomeTaxDeduction(0L)).isEqualTo(480_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(5_000_000L)).isEqualTo(480_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_000L)).isEqualTo(480_000L);
        }

        @Test
        void shouldTaperOffAbove24M() {
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_001L)).isEqualTo(320_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_000L)).isEqualTo(320_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_001L)).isEqualTo(160_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_000L)).isEqualTo(160_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_001L)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Reiwa 7 Strategy (2025)")
    @SuppressWarnings("unused")
    class Reiwa7StrategyTests {

        private final BasicDeductionStrategy strategy = new Reiwa7BasicDeductionStrategy();

        @Test
        void shouldCalculateCorrectDeductionsAcrossAllBrackets() {
            // <= 1.32M -> 950,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(0L)).isEqualTo(950_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(1_320_000L)).isEqualTo(950_000L);

            // 1.32M ~ 3.36M -> 880,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(1_320_001L)).isEqualTo(880_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(3_360_000L)).isEqualTo(880_000L);

            // 3.36M ~ 4.89M -> 680,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(3_360_001L)).isEqualTo(680_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(4_890_000L)).isEqualTo(680_000L);

            // 4.89M ~ 6.55M -> 630,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(4_890_001L)).isEqualTo(630_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_550_000L)).isEqualTo(630_000L);

            // 6.55M ~ 23.5M -> 580,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(6_550_001L)).isEqualTo(580_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(23_500_000L)).isEqualTo(580_000L);

            // 23.5M ~ 24M -> 480,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(23_500_001L)).isEqualTo(480_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_000L)).isEqualTo(480_000L);

            // 24M ~ 24.5M -> 320,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_001L)).isEqualTo(320_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_000L)).isEqualTo(320_000L);

            // 24.5M ~ 25M -> 160,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_001L)).isEqualTo(160_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_000L)).isEqualTo(160_000L);

            // > 25M -> 0 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_001L)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Reiwa 8 & 9 Strategy (2026-2027)")
    @SuppressWarnings("unused")
    class Reiwa8And9StrategyTests {

        private final BasicDeductionStrategy strategy = new Reiwa8And9BasicDeductionStrategy();

        @Test
        void shouldApply1040000SpecialDeductionUpTo4890000() {
            // Verified NTA No.1199 rowspan="3": 132M, 3.36M, 4.89M are all 1,040,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(0L)).isEqualTo(1_040_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(1_320_000L)).isEqualTo(1_040_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(3_360_000L)).isEqualTo(1_040_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(4_890_000L)).isEqualTo(1_040_000L);
        }

        @Test
        void shouldApply670000For489MTo655M() {
            assertThat(strategy.calculateIncomeTaxDeduction(4_890_001L)).isEqualTo(670_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_550_000L)).isEqualTo(670_000L);
        }

        @Test
        void shouldApplyStandardAndTaperOffAbove655M() {
            // 6.55M ~ 23.5M -> 620,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(6_550_001L)).isEqualTo(620_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(23_500_000L)).isEqualTo(620_000L);

            // 23.5M ~ 24M -> 480,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(23_500_001L)).isEqualTo(480_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_000L)).isEqualTo(480_000L);

            // 24M ~ 24.5M -> 320,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_001L)).isEqualTo(320_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_000L)).isEqualTo(320_000L);

            // 24.5M ~ 25M -> 160,000 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_001L)).isEqualTo(160_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_000L)).isEqualTo(160_000L);

            // > 25M -> 0 JPY
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_001L)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Reiwa 10 Onwards Strategy (>= 2028)")
    @SuppressWarnings("unused")
    class Reiwa10OnwardsStrategyTests {

        private final BasicDeductionStrategy strategy = new Reiwa10OnwardsBasicDeductionStrategy();

        @Test
        void shouldApply990000ForLowIncome() {
            assertThat(strategy.calculateIncomeTaxDeduction(0L)).isEqualTo(990_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(1_320_000L)).isEqualTo(990_000L);
        }

        @Test
        void shouldApply620000PermanentBaselineUpTo235M() {
            assertThat(strategy.calculateIncomeTaxDeduction(1_320_001L)).isEqualTo(620_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(3_360_000L)).isEqualTo(620_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(4_890_000L)).isEqualTo(620_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(6_550_000L)).isEqualTo(620_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(23_500_000L)).isEqualTo(620_000L);
        }

        @Test
        void shouldTaperOffAbove235M() {
            assertThat(strategy.calculateIncomeTaxDeduction(23_500_001L)).isEqualTo(480_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_000L)).isEqualTo(480_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_000_001L)).isEqualTo(320_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_000L)).isEqualTo(320_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(24_500_001L)).isEqualTo(160_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_000L)).isEqualTo(160_000L);
            assertThat(strategy.calculateIncomeTaxDeduction(25_000_001L)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Inhabitant Tax Deduction (地方税法第314条の2第2項)")
    @SuppressWarnings("unused")
    class InhabitantTaxDeductionTests {

        private final BasicDeductionStrategy strategy = new Reiwa8And9BasicDeductionStrategy();

        @Test
        void shouldReturnStandard430kBelow24M() {
            assertThat(strategy.calculateInhabitantTaxDeduction(0L)).isEqualTo(430_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(5_000_000L)).isEqualTo(430_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(23_050_000L)).isEqualTo(430_000L); // 25M gross salary baseline
            assertThat(strategy.calculateInhabitantTaxDeduction(24_000_000L)).isEqualTo(430_000L);
        }

        @Test
        void shouldTaperOffAbove24M() {
            assertThat(strategy.calculateInhabitantTaxDeduction(24_000_001L)).isEqualTo(290_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(24_500_000L)).isEqualTo(290_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(24_500_001L)).isEqualTo(150_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(25_000_000L)).isEqualTo(150_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(25_000_001L)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Strategy Factory Routing")
    @SuppressWarnings("unused")
    class FactoryRoutingTests {

        @Test
        void shouldRouteIncomeTaxYearsCorrectly() {
            assertThat(factory.getIncomeTaxStrategy(2020)).isInstanceOf(LegacyBasicDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2024)).isInstanceOf(LegacyBasicDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2025)).isInstanceOf(Reiwa7BasicDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2026)).isInstanceOf(Reiwa8And9BasicDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2027)).isInstanceOf(Reiwa8And9BasicDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2028)).isInstanceOf(Reiwa10OnwardsBasicDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2035)).isInstanceOf(Reiwa10OnwardsBasicDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2060)).isInstanceOf(Reiwa10OnwardsBasicDeductionStrategy.class);
        }

        @Test
        void shouldRouteInhabitantTaxYearsCorrectly() {
            assertThat(factory.getInhabitantTaxStrategy(2024)).isInstanceOf(LegacyBasicDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2025)).isInstanceOf(LegacyBasicDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2026)).isInstanceOf(Reiwa7BasicDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2027)).isInstanceOf(Reiwa8And9BasicDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2028)).isInstanceOf(Reiwa8And9BasicDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2029)).isInstanceOf(Reiwa10OnwardsBasicDeductionStrategy.class);
        }
    }
}

