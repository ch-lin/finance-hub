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
import java.time.temporal.ChronoUnit;

import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;

/**
 * Abstract template base class for {@link DependentDeductionStrategy} implementations.
 * Encapsulates common Japanese statutory age bracket rules and threshold qualification logic.
 */
public abstract class AbstractDependentDeductionStrategy implements DependentDeductionStrategy {

    // General Dependent (16-18, 23-69)
    protected static final long GENERAL_DEPENDENT_INCOME_TAX = 380_000L;
    protected static final long GENERAL_DEPENDENT_INHABITANT_TAX = 330_000L;

    // Specific Dependent (19-22)
    protected static final long SPECIFIC_DEPENDENT_INCOME_TAX = 630_000L;
    protected static final long SPECIFIC_DEPENDENT_INHABITANT_TAX = 450_000L;

    // Elderly Dependent (70+)
    protected static final long ELDERLY_DEPENDENT_INCOME_TAX = 480_000L;
    protected static final long ELDERLY_DEPENDENT_INHABITANT_TAX = 380_000L;

    // Elderly Cohabiting Ascendant Dependent (同居老親等 70+)
    protected static final long ELDERLY_COHABITING_ASCENDANT_INCOME_TAX = 580_000L;
    protected static final long ELDERLY_COHABITING_ASCENDANT_INHABITANT_TAX = 450_000L;

    @Override
    public long calculateIncomeTaxDeduction(Dependent dependent, LocalDate simulationYearEnd) {
        if (dependent == null) {
            return 0L;
        }
        if (dependent.getMemberType() == DependentType.SPOUSE) {
            return 0L;
        }
        return calculateIncomeTaxDeduction(dependent.getEstimatedAnnualIncome(), dependent.getBirthDate(),
                simulationYearEnd, dependent.isCohabiting());
    }

    @Override
    public long calculateInhabitantTaxDeduction(Dependent dependent, LocalDate simulationYearEnd) {
        if (dependent == null) {
            return 0L;
        }
        if (dependent.getMemberType() == DependentType.SPOUSE) {
            return 0L;
        }
        return calculateInhabitantTaxDeduction(dependent.getEstimatedAnnualIncome(), dependent.getBirthDate(),
                simulationYearEnd, dependent.isCohabiting());
    }

    @Override
    public long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd) {
        return calculateIncomeTaxDeduction(estimatedAnnualIncome, birthDate, simulationYearEnd, false);
    }

    @Override
    public long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate,
            LocalDate simulationYearEnd, boolean isCohabiting) {
        long income = estimatedAnnualIncome != null ? estimatedAnnualIncome : 0L;
        Long age = calculateAge(birthDate, simulationYearEnd);

        if (age != null && age >= 19 && age <= 22) {
            if (isEligible(income, age)) {
                return SPECIFIC_DEPENDENT_INCOME_TAX;
            }
            return calculateSpecificDependentSpecialIncomeTaxDeduction(income);
        }

        if (!isEligible(income, age)) {
            return 0L;
        }

        if (age == null) {
            return GENERAL_DEPENDENT_INCOME_TAX;
        }

        if (age < 16) {
            return 0L;
        } else if (age >= 70) {
            return isCohabiting ? ELDERLY_COHABITING_ASCENDANT_INCOME_TAX : ELDERLY_DEPENDENT_INCOME_TAX;
        } else {
            return GENERAL_DEPENDENT_INCOME_TAX;
        }
    }

    @Override
    public long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd) {
        return calculateInhabitantTaxDeduction(estimatedAnnualIncome, birthDate, simulationYearEnd, false);
    }

    @Override
    public long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate,
            LocalDate simulationYearEnd, boolean isCohabiting) {
        long income = estimatedAnnualIncome != null ? estimatedAnnualIncome : 0L;
        Long age = calculateAge(birthDate, simulationYearEnd);

        if (age != null && age >= 19 && age <= 22) {
            if (isEligible(income, age)) {
                return SPECIFIC_DEPENDENT_INHABITANT_TAX;
            }
            return calculateSpecificDependentSpecialInhabitantTaxDeduction(income);
        }

        if (!isEligible(income, age)) {
            return 0L;
        }

        if (age == null) {
            return GENERAL_DEPENDENT_INHABITANT_TAX;
        }

        if (age < 16) {
            return 0L;
        } else if (age >= 70) {
            return isCohabiting ? ELDERLY_COHABITING_ASCENDANT_INHABITANT_TAX : ELDERLY_DEPENDENT_INHABITANT_TAX;
        } else {
            return GENERAL_DEPENDENT_INHABITANT_TAX;
        }
    }

    /**
     * Calculates the special income tax deduction for specific dependents (age 19-22)
     * whose wage income exceeds the standard eligibility threshold.
     * Default implementation returns 0L (legacy behavior).
     *
     * @param wageIncome annual wage income in JPY
     * @return income tax deduction in JPY
     */
    protected long calculateSpecificDependentSpecialIncomeTaxDeduction(long wageIncome) {
        return 0L;
    }

    /**
     * Calculates the special personal inhabitant tax deduction for specific dependents (age 19-22)
     * whose wage income exceeds the standard eligibility threshold.
     * Default implementation returns 0L (legacy behavior).
     *
     * @param wageIncome annual wage income in JPY
     * @return inhabitant tax deduction in JPY
     */
    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
        return 0L;
    }

    /**
     * Checks whether the dependent's annual income is within the statutory eligibility threshold.
     * Elderly relatives (age >= 65) are eligible up to the pension threshold (公的年金等控除 110 萬 + 所得門檻),
     * while general relatives are evaluated against the wage threshold (給與所得控除 + 所得門檻).
     *
     * @param income annual income in JPY
     * @param age age at simulation year-end, or null if unknown
     * @return true if income qualifies for dependent deduction
     */
    protected boolean isEligible(long income, Long age) {
        long threshold = (age != null && age >= 65) ? getElderlyPensionThreshold() : getWageIncomeThreshold();
        return income <= threshold;
    }

    /**
     * Calculates the age of the dependent as of the simulation year-end.
     *
     * @param birthDate birth date
     * @param simulationYearEnd simulation year-end date
     * @return age in years, or null if birthDate or simulationYearEnd is null
     */
    protected Long calculateAge(LocalDate birthDate, LocalDate simulationYearEnd) {
        if (birthDate == null || simulationYearEnd == null) {
            return null;
        }
        return ChronoUnit.YEARS.between(birthDate, simulationYearEnd);
    }
}
