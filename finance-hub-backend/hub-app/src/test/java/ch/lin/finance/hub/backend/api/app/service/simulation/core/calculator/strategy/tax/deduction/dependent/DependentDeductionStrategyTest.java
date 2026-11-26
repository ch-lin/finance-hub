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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.dependent;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;

class DependentDeductionStrategyTest {

    private final DependentDeductionStrategyFactory factory = new DependentDeductionStrategyFactory();

    @Nested
    @DisplayName("Strategy Factory Dispatch Tests")
    @SuppressWarnings("unused")
    class FactoryTests {

        @Test
        void shouldResolveIncomeTaxStrategy() {
            // Income tax: assesses current year income
            assertThat(factory.getIncomeTaxStrategy(2020)).isInstanceOf(LegacyDependentDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2024)).isInstanceOf(LegacyDependentDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2025)).isInstanceOf(Reiwa7DependentDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2026)).isInstanceOf(Reiwa8And9DependentDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2027)).isInstanceOf(Reiwa8And9DependentDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2028)).isInstanceOf(Reiwa10OnwardsDependentDeductionStrategy.class);
            assertThat(factory.getIncomeTaxStrategy(2035)).isInstanceOf(Reiwa10OnwardsDependentDeductionStrategy.class);
        }

        @Test
        void shouldResolveInhabitantTaxStrategy() {
            // Inhabitant tax: assesses prior year income (1-year lag)
            assertThat(factory.getInhabitantTaxStrategy(2024)).isInstanceOf(LegacyDependentDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2025)).isInstanceOf(LegacyDependentDeductionStrategy.class);
            // 2026 simulation assesses 2025 (Reiwa 7) income
            assertThat(factory.getInhabitantTaxStrategy(2026)).isInstanceOf(Reiwa7DependentDeductionStrategy.class);
            // 2027-2028 simulation assesses 2026-2027 (Reiwa 8-9) income
            assertThat(factory.getInhabitantTaxStrategy(2027)).isInstanceOf(Reiwa8And9DependentDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2028)).isInstanceOf(Reiwa8And9DependentDeductionStrategy.class);
            // 2029+ simulation assesses 2028+ (Reiwa 10 Onwards) income
            assertThat(factory.getInhabitantTaxStrategy(2029)).isInstanceOf(Reiwa10OnwardsDependentDeductionStrategy.class);
            assertThat(factory.getInhabitantTaxStrategy(2035)).isInstanceOf(Reiwa10OnwardsDependentDeductionStrategy.class);
        }
    }

    @Nested
    @DisplayName("Statutory Threshold Getters Tests")
    @SuppressWarnings("unused")
    class ThresholdGetterTests {

        @Test
        void shouldReturnCorrectStatutoryThresholds() {
            DependentDeductionStrategy legacy = new LegacyDependentDeductionStrategy();
            assertThat(legacy.getWageIncomeThreshold()).isEqualTo(1_030_000L);
            assertThat(legacy.getTotalIncomeThreshold()).isEqualTo(480_000L);
            assertThat(legacy.getElderlyPensionThreshold()).isEqualTo(1_580_000L);

            DependentDeductionStrategy reiwa7 = new Reiwa7DependentDeductionStrategy();
            assertThat(reiwa7.getWageIncomeThreshold()).isEqualTo(1_230_000L);
            assertThat(reiwa7.getTotalIncomeThreshold()).isEqualTo(580_000L);
            assertThat(reiwa7.getElderlyPensionThreshold()).isEqualTo(1_680_000L);

            DependentDeductionStrategy reiwa8And9 = new Reiwa8And9DependentDeductionStrategy();
            assertThat(reiwa8And9.getWageIncomeThreshold()).isEqualTo(1_360_000L);
            assertThat(reiwa8And9.getTotalIncomeThreshold()).isEqualTo(620_000L);
            assertThat(reiwa8And9.getElderlyPensionThreshold()).isEqualTo(1_720_000L);

            DependentDeductionStrategy reiwa10 = new Reiwa10OnwardsDependentDeductionStrategy();
            assertThat(reiwa10.getWageIncomeThreshold()).isEqualTo(1_310_000L);
            assertThat(reiwa10.getTotalIncomeThreshold()).isEqualTo(620_000L);
            assertThat(reiwa10.getElderlyPensionThreshold()).isEqualTo(1_720_000L);
        }
    }

    @Nested
    @DisplayName("Age Classification & Deduction Amount Tests")
    @SuppressWarnings("unused")
    class AgeClassificationTests {

        private final DependentDeductionStrategy strategy = new Reiwa8And9DependentDeductionStrategy();
        private final LocalDate yearEnd = LocalDate.of(2026, 12, 31);

        @Test
        void shouldHandleNullDependentAndSpouseExclusion() {
            assertThat(strategy.calculateIncomeTaxDeduction(null, yearEnd)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(null, yearEnd)).isZero();

            Dependent spouse = Dependent.builder()
                    .memberType(DependentType.SPOUSE)
                    .birthDate(LocalDate.of(1990, 5, 1))
                    .estimatedAnnualIncome(500_000L)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(spouse, yearEnd)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(spouse, yearEnd)).isZero();
        }

        @Test
        void shouldFallbackToGeneralDependentWhenBirthDateIsNull() {
            Dependent dependent = Dependent.builder()
                    .memberType(DependentType.DEPENDENT)
                    .birthDate(null)
                    .estimatedAnnualIncome(500_000L)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(dependent, yearEnd)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(dependent, yearEnd)).isEqualTo(330_000L);
        }

        @Test
        void shouldReturnZeroForYoungDependentUnder16() {
            // Age 15 (Born 2011-01-01)
            Dependent child = Dependent.builder()
                    .memberType(DependentType.DEPENDENT)
                    .birthDate(LocalDate.of(2011, 1, 1))
                    .estimatedAnnualIncome(0L)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(child, yearEnd)).isZero();
            assertThat(strategy.calculateInhabitantTaxDeduction(child, yearEnd)).isZero();
        }

        @Test
        void shouldReturnGeneralDeductionForAge16To18() {
            // Age 17 (Born 2009-06-15)
            Dependent teen = Dependent.builder()
                    .memberType(DependentType.DEPENDENT)
                    .birthDate(LocalDate.of(2009, 6, 15))
                    .estimatedAnnualIncome(500_000L)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(teen, yearEnd)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(teen, yearEnd)).isEqualTo(330_000L);
        }

        @Test
        void shouldReturnSpecificDeductionForAge19To22() {
            // Age 20 (Born 2006-03-20)
            Dependent student = Dependent.builder()
                    .memberType(DependentType.DEPENDENT)
                    .birthDate(LocalDate.of(2006, 3, 20))
                    .estimatedAnnualIncome(1_000_000L)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(student, yearEnd)).isEqualTo(630_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(student, yearEnd)).isEqualTo(450_000L);
        }

        @Test
        void shouldReturnGeneralDeductionForAge23To69() {
            // Age 30 (Born 1996-01-01)
            Dependent adult = Dependent.builder()
                    .memberType(DependentType.DEPENDENT)
                    .birthDate(LocalDate.of(1996, 1, 1))
                    .estimatedAnnualIncome(800_000L)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(adult, yearEnd)).isEqualTo(380_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(adult, yearEnd)).isEqualTo(330_000L);
        }

        @Test
        void shouldReturnElderlyDeductionForAge70AndAbove() {
            // Age 72 (Born 1954-05-10)
            Dependent senior = Dependent.builder()
                    .memberType(DependentType.DEPENDENT)
                    .birthDate(LocalDate.of(1954, 5, 10))
                    .estimatedAnnualIncome(1_500_000L)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(senior, yearEnd)).isEqualTo(480_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(senior, yearEnd)).isEqualTo(380_000L);
        }

        @Test
        void shouldReturnElderlyCohabitingAscendantDeductionForAge70AndAboveWhenLivingTogether() {
            // Age 72 (Born 1954-05-10) with isCohabiting = true (同居老親等)
            Dependent cohabitingSenior = Dependent.builder()
                    .memberType(DependentType.DEPENDENT)
                    .birthDate(LocalDate.of(1954, 5, 10))
                    .estimatedAnnualIncome(1_500_000L)
                    .isCohabiting(true)
                    .build();
            assertThat(strategy.calculateIncomeTaxDeduction(cohabitingSenior, yearEnd)).isEqualTo(580_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(cohabitingSenior, yearEnd)).isEqualTo(450_000L);

            // Also test 4-arg overload directly
            assertThat(strategy.calculateIncomeTaxDeduction(1_500_000L, LocalDate.of(1954, 5, 10), yearEnd, true))
                    .isEqualTo(580_000L);
            assertThat(strategy.calculateInhabitantTaxDeduction(1_500_000L, LocalDate.of(1954, 5, 10), yearEnd, true))
                    .isEqualTo(450_000L);
        }
    }

    @Nested
    @DisplayName("Era-Specific Income Boundary & Pension Tests")
    @SuppressWarnings("unused")
    class BoundaryTests {

        private final LocalDate yearEnd2026 = LocalDate.of(2026, 12, 31);
        private final LocalDate birth1990 = LocalDate.of(1990, 1, 1); // General (age 36)
        private final LocalDate birth1955 = LocalDate.of(1955, 1, 1); // Senior (age 71, >=65)

        @Test
        void shouldEnforceLegacy103MAnd158MBoundary() {
            DependentDeductionStrategy legacy = new LegacyDependentDeductionStrategy();
            // Wage boundary for general dependent: <= 1.03M qualifies, > 1.03M disqualifies
            assertThat(legacy.calculateIncomeTaxDeduction(1_030_000L, birth1990, yearEnd2026)).isEqualTo(380_000L);
            assertThat(legacy.calculateIncomeTaxDeduction(1_030_001L, birth1990, yearEnd2026)).isZero();

            // Pension boundary for 65+ senior: <= 1.58M qualifies, > 1.58M disqualifies
            assertThat(legacy.calculateIncomeTaxDeduction(1_580_000L, birth1955, yearEnd2026)).isEqualTo(480_000L);
            assertThat(legacy.calculateIncomeTaxDeduction(1_580_001L, birth1955, yearEnd2026)).isZero();
        }

        @Test
        void shouldEnforceReiwa7123MAnd168MBoundary() {
            DependentDeductionStrategy reiwa7 = new Reiwa7DependentDeductionStrategy();
            // Wage boundary for general dependent: <= 1.23M qualifies, > 1.23M disqualifies
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_230_000L, birth1990, yearEnd2026)).isEqualTo(380_000L);
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_230_001L, birth1990, yearEnd2026)).isZero();

            // Pension boundary for 65+ senior: <= 1.68M qualifies, > 1.68M disqualifies
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_680_000L, birth1955, yearEnd2026)).isEqualTo(480_000L);
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_680_001L, birth1955, yearEnd2026)).isZero();
        }

        @Test
        void shouldEnforceReiwa8And9136MAnd172MBoundary() {
            DependentDeductionStrategy reiwa8And9 = new Reiwa8And9DependentDeductionStrategy();
            // Wage boundary: <= 1.36M qualifies, > 1.36M disqualifies
            assertThat(reiwa8And9.calculateIncomeTaxDeduction(1_360_000L, birth1990, yearEnd2026)).isEqualTo(380_000L);
            assertThat(reiwa8And9.calculateIncomeTaxDeduction(1_360_001L, birth1990, yearEnd2026)).isZero();

            // Pension boundary for 65+ senior: <= 1.72M qualifies, > 1.72M disqualifies
            assertThat(reiwa8And9.calculateIncomeTaxDeduction(1_720_000L, birth1955, yearEnd2026)).isEqualTo(480_000L);
            assertThat(reiwa8And9.calculateIncomeTaxDeduction(1_720_001L, birth1955, yearEnd2026)).isZero();
        }

        @Test
        void shouldEnforceReiwa10Onwards131MAnd172MBoundary() {
            DependentDeductionStrategy reiwa10 = new Reiwa10OnwardsDependentDeductionStrategy();
            // Wage boundary reverts to 1.31M post-sunset: <= 1.31M qualifies, > 1.31M disqualifies
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_310_000L, birth1990, yearEnd2026)).isEqualTo(380_000L);
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_310_001L, birth1990, yearEnd2026)).isZero();

            // Pension boundary remains 1.72M: <= 1.72M qualifies, > 1.72M disqualifies
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_720_000L, birth1955, yearEnd2026)).isEqualTo(480_000L);
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_720_001L, birth1955, yearEnd2026)).isZero();
        }
    }

    @Nested
    @DisplayName("Specific Dependent (Age 19-22) Special Deduction Step Tests")
    @SuppressWarnings("unused")
    class SpecificDependentSpecialDeductionTests {

        private final LocalDate yearEnd2026 = LocalDate.of(2026, 12, 31);
        private final LocalDate studentBirth2006 = LocalDate.of(2006, 5, 20); // Age 20 (19 <= age <= 22)
        private final LocalDate teenBirth2009 = LocalDate.of(2009, 5, 20); // Age 17 (not specific)
        private final LocalDate adultBirth1995 = LocalDate.of(1995, 5, 20); // Age 31 (not specific)

        @Test
        void shouldCalculateReiwa8And9SteppedDeductions() {
            DependentDeductionStrategy reiwa8 = new Reiwa8And9DependentDeductionStrategy();

            // Standard eligible: <= 1.36M -> Full 630K / 450K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_360_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_360_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Buffer zone: 1.36M < wage <= 1.59M -> 100% full deduction 630K / 450K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_590_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_590_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Step 1: 1.59M < wage <= 1.64M -> Income tax 610K / Inhabitant tax 450K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_600_000L, studentBirth2006, yearEnd2026)).isEqualTo(610_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_600_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Step 2: 1.64M < wage <= 1.69M -> Income tax 510K / Inhabitant tax 450K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_650_000L, studentBirth2006, yearEnd2026)).isEqualTo(510_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_650_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Step 3: 1.69M < wage <= 1.74M -> Income tax 410K / Inhabitant tax 410K (Synchronized!)
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_700_000L, studentBirth2006, yearEnd2026)).isEqualTo(410_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_700_000L, studentBirth2006, yearEnd2026)).isEqualTo(410_000L);

            // Step 4: 1.74M < wage <= 1.79M -> 310K / 310K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_750_000L, studentBirth2006, yearEnd2026)).isEqualTo(310_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_750_000L, studentBirth2006, yearEnd2026)).isEqualTo(310_000L);

            // Step 5: 1.79M < wage <= 1.84M -> 210K / 210K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_800_000L, studentBirth2006, yearEnd2026)).isEqualTo(210_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_800_000L, studentBirth2006, yearEnd2026)).isEqualTo(210_000L);

            // Step 6: 1.84M < wage <= 1.89M -> 110K / 110K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_850_000L, studentBirth2006, yearEnd2026)).isEqualTo(110_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_850_000L, studentBirth2006, yearEnd2026)).isEqualTo(110_000L);

            // Step 7: 1.89M < wage <= 1.94M -> 60K / 60K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_900_000L, studentBirth2006, yearEnd2026)).isEqualTo(60_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_900_000L, studentBirth2006, yearEnd2026)).isEqualTo(60_000L);

            // Final Step: 1.94M < wage <= 1.97M -> 30K / 30K
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_970_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_970_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);

            // Beyond 1.97M -> 0
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_970_001L, studentBirth2006, yearEnd2026)).isZero();
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_970_001L, studentBirth2006, yearEnd2026)).isZero();
        }

        @Test
        void shouldCalculateReiwa7SteppedDeductions() {
            DependentDeductionStrategy reiwa7 = new Reiwa7DependentDeductionStrategy();

            // Standard eligible: <= 1.23M -> Full 630K / 450K
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_230_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_230_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Buffer zone: 1.23M < wage <= 1.50M -> 630K / 450K
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_400_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_400_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Step 1: 1.50M < wage <= 1.55M -> 610K / 450K
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_520_000L, studentBirth2006, yearEnd2026)).isEqualTo(610_000L);
            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_520_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Final Step: 1.85M < wage <= 1.88M -> 30K / 30K
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_880_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_880_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);

            // Beyond 1.88M -> 0
            assertThat(reiwa7.calculateIncomeTaxDeduction(1_880_001L, studentBirth2006, yearEnd2026)).isZero();
            assertThat(reiwa7.calculateInhabitantTaxDeduction(1_880_001L, studentBirth2006, yearEnd2026)).isZero();
        }

        @Test
        void shouldCalculateReiwa10OnwardsSteppedDeductions() {
            DependentDeductionStrategy reiwa10 = new Reiwa10OnwardsDependentDeductionStrategy();

            // Standard eligible: <= 1.31M -> Full 630K / 450K
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_310_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_310_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Buffer zone: 1.31M < wage <= 1.54M -> 630K / 450K
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(630_000L);
            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isEqualTo(450_000L);

            // Final Step: 1.89M < wage <= 1.92M -> 30K / 30K
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_920_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);
            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_920_000L, studentBirth2006, yearEnd2026)).isEqualTo(30_000L);

            // Beyond 1.92M -> 0
            assertThat(reiwa10.calculateIncomeTaxDeduction(1_920_001L, studentBirth2006, yearEnd2026)).isZero();
            assertThat(reiwa10.calculateInhabitantTaxDeduction(1_920_001L, studentBirth2006, yearEnd2026)).isZero();
        }

        @Test
        void shouldNotApplySpecialDeductionToLegacyOrNonSpecificAge() {
            DependentDeductionStrategy legacy = new LegacyDependentDeductionStrategy();
            DependentDeductionStrategy reiwa8 = new Reiwa8And9DependentDeductionStrategy();

            // Legacy has no special deduction: > 1.03M is always 0 even for student
            assertThat(legacy.calculateIncomeTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isZero();
            assertThat(legacy.calculateInhabitantTaxDeduction(1_500_000L, studentBirth2006, yearEnd2026)).isZero();

            // Reiwa 8: non-specific age (teen 17 or adult 31) exceeding threshold gets 0
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_500_000L, teenBirth2009, yearEnd2026)).isZero();
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_500_000L, teenBirth2009, yearEnd2026)).isZero();
            assertThat(reiwa8.calculateIncomeTaxDeduction(1_500_000L, adultBirth1995, yearEnd2026)).isZero();
            assertThat(reiwa8.calculateInhabitantTaxDeduction(1_500_000L, adultBirth1995, yearEnd2026)).isZero();
        }
    }
}
