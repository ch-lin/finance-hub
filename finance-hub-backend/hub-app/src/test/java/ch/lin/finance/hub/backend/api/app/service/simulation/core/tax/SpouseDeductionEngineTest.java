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

import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;

class SpouseDeductionEngineTest {

    private SpouseDeductionEngine engine;
    private LocalDate yearEnd2026;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        engine = new SpouseDeductionEngine();
        yearEnd2026 = LocalDate.of(2026, 12, 31);
    }

    @Test
    @DisplayName("Should return 0 deduction if spouse is null")
    void calculate_nullSpouse_returnsZero() {
        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, null, yearEnd2026)).isZero();
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, null, yearEnd2026)).isZero();
    }

    @Test
    @DisplayName("Should return 0 deduction if user gross income exceeds threshold (11,950,000)")
    void calculate_highUserIncome_returnsZero() {
        Dependent spouse = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1956, 1, 1))
                .estimatedAnnualIncome(500_000L)
                .build();

        assertThat(engine.calculateIncomeTaxDeduction(12_000_000L, spouse, yearEnd2026)).isZero();
        assertThat(engine.calculateInhabitantTaxDeduction(12_000_000L, spouse, yearEnd2026)).isZero();
    }

    @Test
    @DisplayName("Should return standard deduction (380k / 330k) for spouse under 70 years old")
    void calculate_spouseUnder70_returnsStandardDeduction() {
        // Born on 1957-01-01 -> 69 years old on 2026-12-31
        Dependent spouse = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1957, 1, 1))
                .estimatedAnnualIncome(1_000_000L)
                .build();

        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouse, yearEnd2026)).isEqualTo(380_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouse, yearEnd2026)).isEqualTo(330_000L);
    }

    @Test
    @DisplayName("Should return elderly deduction (480k / 380k) for spouse aged 70 or older on Dec 31")
    void calculate_spouse70OrOlder_returnsElderlyDeduction() {
        // Born on 1956-12-31 -> exactly 70 years old on 2026-12-31
        Dependent spouseExactly70 = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1956, 12, 31))
                .estimatedAnnualIncome(1_000_000L)
                .build();

        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouseExactly70, yearEnd2026)).isEqualTo(480_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouseExactly70, yearEnd2026)).isEqualTo(380_000L);

        // Born on 1951-05-15 -> 75 years old on 2026-12-31
        Dependent spouseAge75 = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1951, 5, 15))
                .estimatedAnnualIncome(0L)
                .build();

        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouseAge75, yearEnd2026)).isEqualTo(480_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouseAge75, yearEnd2026)).isEqualTo(380_000L);
    }

    @Test
    @DisplayName("Should fallback to standard deduction (380k / 330k) if birthDate is null")
    void calculate_nullBirthDate_fallbacksToStandardDeduction() {
        Dependent spouse = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(null)
                .estimatedAnnualIncome(800_000L)
                .build();

        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouse, yearEnd2026)).isEqualTo(380_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouse, yearEnd2026)).isEqualTo(330_000L);
    }

    @Test
    @DisplayName("Should return special spouse deduction without elderly bonus even if spouse is over 70")
    void calculate_specialSpouseDeduction_noElderlyBonus() {
        // In Reiwa 8: Income between 1.36M and 1.69M gives full 380k/330k deduction, but NO elderly bonus (>1.36M)
        Dependent spouseAge75At150M = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1951, 1, 1))
                .estimatedAnnualIncome(1_500_000L)
                .build();

        // 380k / 330k (NOT 480k / 380k because special spouse deduction has no elderly bonus)
        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouseAge75At150M, yearEnd2026))
                .isEqualTo(380_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouseAge75At150M, yearEnd2026))
                .isEqualTo(330_000L);

        // At 1.80M:
        // - 2026 Income Tax uses Reiwa 8 (within 1.74M~1.80M bracket) -> 310,000L
        // - 2026 Inhabitant Tax uses Reiwa 7 (assesses 2025 income, 1.75M~1.80M bracket) -> 210,000L
        Dependent spouseAge75At180M = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1951, 1, 1))
                .estimatedAnnualIncome(1_800_000L)
                .build();

        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouseAge75At180M, yearEnd2026))
                .isEqualTo(310_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouseAge75At180M, yearEnd2026))
                .isEqualTo(210_000L);

        // In 2027: Inhabitant Tax switches to Reiwa 8 -> 310,000L
        LocalDate yearEnd2027 = LocalDate.of(2027, 12, 31);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouseAge75At180M, yearEnd2027))
                .isEqualTo(310_000L);
    }

    @Test
    @DisplayName("Should return 0 deduction if spouse income exceeds 2,070,000 in 2026")
    void calculate_spouseIncomeExceedsCap_returnsZero() {
        Dependent spouse = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1950, 1, 1))
                .estimatedAnnualIncome(2_500_000L)
                .build();

        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouse, yearEnd2026)).isZero();
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouse, yearEnd2026)).isZero();
    }

    @Test
    @DisplayName("Should delegate to Legacy strategy when year is 2025 or earlier")
    void calculate_legacyYear_delegatesCorrectly() {
        LocalDate yearEnd2024 = LocalDate.of(2024, 12, 31);
        Dependent spouseAt180M = Dependent.builder()
                .memberType(DependentType.SPOUSE)
                .birthDate(LocalDate.of(1980, 1, 1))
                .estimatedAnnualIncome(1_800_000L)
                .build();

        // In 2024 (Legacy), 1.8M falls in 1.752M~1.836M bracket -> 210,000L / 210,000L
        assertThat(engine.calculateIncomeTaxDeduction(6_000_000L, spouseAt180M, yearEnd2024)).isEqualTo(210_000L);
        assertThat(engine.calculateInhabitantTaxDeduction(6_000_000L, spouseAt180M, yearEnd2024)).isEqualTo(210_000L);
    }
}
