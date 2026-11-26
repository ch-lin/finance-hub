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

/**
 * Strategy interface for calculating spouse deductions (配偶者控除及び配偶者特別控除)
 * for income tax (所得税) and inhabitant tax (住民税) based on taxpayer gross salary,
 * spouse gross salary, and elderly spouse status.
 */
public interface SpouseDeductionStrategy {

    /**
     * Calculates the spouse deduction for income tax (所得税の配偶者控除・配偶者特別控除).
     *
     * @param userGrossSalary taxpayer's gross annual salary in JPY
     * @param spouseGrossSalary spouse's gross annual salary in JPY
     * @param isElderlySpouse true if spouse is 70 years of age or older as of Dec 31
     * @return deduction amount in JPY
     */
    long calculateIncomeTaxDeduction(long userGrossSalary, long spouseGrossSalary, boolean isElderlySpouse);

    /**
     * Calculates the spouse deduction for personal inhabitant tax (住民税の配偶者控除・配偶者特別控除).
     *
     * @param userGrossSalary taxpayer's gross annual salary in JPY
     * @param spouseGrossSalary spouse's gross annual salary in JPY
     * @param isElderlySpouse true if spouse is 70 years of age or older as of Dec 31
     * @return deduction amount in JPY
     */
    long calculateInhabitantTaxDeduction(long userGrossSalary, long spouseGrossSalary, boolean isElderlySpouse);
}
