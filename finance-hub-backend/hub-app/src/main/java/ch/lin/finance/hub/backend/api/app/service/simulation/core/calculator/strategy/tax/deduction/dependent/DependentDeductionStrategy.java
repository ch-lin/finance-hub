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

import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;

/**
 * Strategy interface for calculating dependent deductions (扶養控除)
 * for income tax (所得税) and personal inhabitant tax (住民税) based on
 * dependent age classification and statutory income thresholds.
 */
public interface DependentDeductionStrategy {

    /**
     * Calculates the dependent deduction for income tax (所得税の扶養控除).
     *
     * @param dependent the dependent entity
     * @param simulationYearEnd the simulation year-end date
     * @return deduction amount in JPY
     */
    long calculateIncomeTaxDeduction(Dependent dependent, LocalDate simulationYearEnd);

    /**
     * Calculates the dependent deduction for personal inhabitant tax (住民税の扶養控除).
     *
     * @param dependent the dependent entity
     * @param simulationYearEnd the simulation year-end date
     * @return deduction amount in JPY
     */
    long calculateInhabitantTaxDeduction(Dependent dependent, LocalDate simulationYearEnd);

    /**
     * Overloaded convenience method to calculate income tax deduction directly from parameters.
     *
     * @param estimatedAnnualIncome estimated annual gross wage or pension in JPY
     * @param birthDate birth date of the dependent
     * @param simulationYearEnd the simulation year-end date
     * @return deduction amount in JPY
     */
    long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd);

    /**
     * Overloaded convenience method to calculate income tax deduction directly from parameters,
     * including cohabitation status.
     *
     * @param estimatedAnnualIncome estimated annual gross wage or pension in JPY
     * @param birthDate birth date of the dependent
     * @param simulationYearEnd the simulation year-end date
     * @param isCohabiting whether the dependent is cohabiting with taxpayer (同居老親等)
     * @return deduction amount in JPY
     */
    long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd,
            boolean isCohabiting);

    /**
     * Overloaded convenience method to calculate personal inhabitant tax deduction directly from parameters.
     *
     * @param estimatedAnnualIncome estimated annual gross wage or pension in JPY
     * @param birthDate birth date of the dependent
     * @param simulationYearEnd the simulation year-end date
     * @return deduction amount in JPY
     */
    long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd);

    /**
     * Overloaded convenience method to calculate personal inhabitant tax deduction directly from parameters,
     * including cohabitation status.
     *
     * @param estimatedAnnualIncome estimated annual gross wage or pension in JPY
     * @param birthDate birth date of the dependent
     * @param simulationYearEnd the simulation year-end date
     * @param isCohabiting whether the dependent is cohabiting with taxpayer (同居老親等)
     * @return deduction amount in JPY
     */
    long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd,
            boolean isCohabiting);

    /**
     * Returns the statutory wage income threshold (給与収入門檻) for this era.
     *
     * @return wage threshold in JPY
     */
    long getWageIncomeThreshold();

    /**
     * Returns the statutory total income threshold (合計所得金額門檻) for this era.
     *
     * @return total income threshold in JPY
     */
    long getTotalIncomeThreshold();

    /**
     * Returns the statutory pension threshold for 65+ elderly (65歳以上公的年金等収入門檻) for this era.
     *
     * @return pension threshold in JPY
     */
    long getElderlyPensionThreshold();
}
