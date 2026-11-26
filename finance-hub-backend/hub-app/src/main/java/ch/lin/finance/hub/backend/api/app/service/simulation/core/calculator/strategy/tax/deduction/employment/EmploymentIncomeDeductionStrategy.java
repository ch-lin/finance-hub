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

/**
 * Strategy interface for calculating statutory employment income deduction
 * (給与所得控除額) based on gross salary income (給与等の収入金額).
 */
public interface EmploymentIncomeDeductionStrategy {

    /**
     * Calculates the statutory employment income deduction in JPY.
     *
     * @param grossSalary gross total salary income (給与等の収入金額) in JPY
     * @return employment income deduction (給与所得控除額) in JPY
     */
    long calculateDeduction(long grossSalary);
}
