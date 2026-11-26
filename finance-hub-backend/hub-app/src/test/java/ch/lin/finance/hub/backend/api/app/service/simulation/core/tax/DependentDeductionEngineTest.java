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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;

class DependentDeductionEngineTest {

    private DependentDeductionEngine engine;
    private LocalDate yearEnd2026;
    private LocalDate yearEnd2024;

    @BeforeEach
    void setUp() {
        engine = new DependentDeductionEngine();
        yearEnd2026 = LocalDate.of(2026, 12, 31);
        yearEnd2024 = LocalDate.of(2024, 12, 31);
    }

    @Test
    @DisplayName("Should return 0 deduction if dependent is null")
    void calculate_nullDependent_returnsZero() {
        assertThat(engine.calculateIncomeTaxDeduction(null, null, yearEnd2026)).isZero();
        assertThat(engine.calculateInhabitantTaxDeduction(null, null, yearEnd2026)).isZero();
        assertThat(engine.calculateIncomeTaxDeduction(null, yearEnd2026)).isZero();
        assertThat(engine.calculateInhabitantTaxDeduction(null, yearEnd2026)).isZero();
    }

    @Test
    @DisplayName("Should delegate to 2026 Reiwa 8-9 strategy and qualify 1.36M wage earner in 2026")
    void calculate_year2026_delegatesToReiwa8And9() {
        // Age 20 (Specific Dependent) earning 1,360,000 JPY
        Dependent dependent = Dependent.builder()
                .memberType(DependentType.DEPENDENT)
                .birthDate(LocalDate.of(2006, 5, 1))
                .estimatedAnnualIncome(1_360_000L)
                .build();

        SimulationContext context = new SimulationContext();
        context.setYear(2026);

        // 2026 Income Tax: qualifies under Reiwa 8-9 (<= 1.36M), specific dependent gets 630,000 JPY
        long incomeTaxDeduction = engine.calculateIncomeTaxDeduction(context, dependent, yearEnd2026);
        assertThat(incomeTaxDeduction).isEqualTo(630_000L);

        // 2026 Inhabitant Tax: assesses 2025 (Reiwa 7) income. Although exceeding standard 1.23M threshold,
        // 1.36M falls within the Reiwa 7 buffer zone (<= 1.50M) for 特定親族特別控除 -> full 450,000 JPY
        long inhabitantTaxDeduction = engine.calculateInhabitantTaxDeduction(context, dependent, yearEnd2026);
        assertThat(inhabitantTaxDeduction).isEqualTo(450_000L);

        // Beyond Reiwa 7 cutoff (> 1.88M, e.g. 1.89M) -> 0 JPY for 2026 inhabitant tax
        dependent.setEstimatedAnnualIncome(1_890_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(context, dependent, yearEnd2026)).isZero();
    }

    @Test
    @DisplayName("Should delegate to Legacy strategy for 2024 year end")
    void calculate_year2024_delegatesToLegacy() {
        // General dependent earning 1,030,000 JPY
        Dependent dependent = Dependent.builder()
                .memberType(DependentType.DEPENDENT)
                .birthDate(LocalDate.of(1995, 3, 1))
                .estimatedAnnualIncome(1_030_000L)
                .build();

        // In 2024: 1.03M is exactly within legacy threshold -> 380,000 JPY
        assertThat(engine.calculateIncomeTaxDeduction(dependent, yearEnd2024)).isEqualTo(380_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(dependent, yearEnd2024)).isEqualTo(330_000L);

        // 1,030,001 JPY exceeds legacy threshold -> 0 JPY
        dependent.setEstimatedAnnualIncome(1_030_001L);
        assertThat(engine.calculateIncomeTaxDeduction(dependent, yearEnd2024)).isZero();
        assertThat(engine.calculateInhabitantTaxDeduction(dependent, yearEnd2024)).isZero();
    }

    @Test
    @DisplayName("Should support 65+ elderly pension threshold up to 1,720,000 in Reiwa 8")
    void calculate_elderlyPension_supportedIn2026() {
        // Senior age 72 (Elderly Dependent) receiving 1,700,000 JPY pension
        Dependent senior = Dependent.builder()
                .memberType(DependentType.DEPENDENT)
                .birthDate(LocalDate.of(1954, 5, 1))
                .estimatedAnnualIncome(1_700_000L)
                .build();

        SimulationContext context = new SimulationContext();
        context.setYear(2026);

        // Income tax qualifies under 1.72M threshold -> 480,000 JPY
        assertThat(engine.calculateIncomeTaxDeduction(context, senior, yearEnd2026)).isEqualTo(480_000L);
    }

    @Test
    @DisplayName("Should differentiate cohabiting and non-cohabiting elderly dependent deduction")
    void calculate_elderlyCohabitingAscendant_differentiatesAmounts() {
        // Senior age 72 with 1,500,000 JPY pension (qualifies for both income tax and inhabitant tax in all eras)
        Dependent senior = Dependent.builder()
                .memberType(DependentType.DEPENDENT)
                .birthDate(LocalDate.of(1954, 5, 1))
                .estimatedAnnualIncome(1_500_000L)
                .build();

        SimulationContext context = new SimulationContext();
        context.setYear(2026);

        // Non-cohabiting: 480,000 JPY income tax, 380,000 JPY inhabitant tax
        assertThat(engine.calculateIncomeTaxDeduction(context, senior, yearEnd2026)).isEqualTo(480_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(context, senior, yearEnd2026)).isEqualTo(380_000L);

        // Cohabiting (同居老親等): 580,000 JPY income tax, 450,000 JPY inhabitant tax
        senior.setIsCohabiting(true);
        assertThat(engine.calculateIncomeTaxDeduction(context, senior, yearEnd2026)).isEqualTo(580_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(context, senior, yearEnd2026)).isEqualTo(450_000L);
    }
}
