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

/**
 * Strategy interface for calculating the dynamic income tax basic deduction
 * (所得税の基礎控除額) based on simulation year and taxpayer's total income (合計所得金額).
 */
public interface BasicDeductionStrategy {

    /**
     * Calculates the basic deduction for income tax based on total income.
     *
     * @param totalIncome total income (給与所得 or 公的年金等雑所得) in JPY
     * @return basic deduction amount for income tax in JPY
     */
    long calculateIncomeTaxDeduction(long totalIncome);

    /**
     * Calculates the basic deduction for inhabitant tax based on total income
     * (地方税法第314条の2第2項).
     *
     * @param totalIncome total income (前年の合計所得金額) in JPY
     * @return basic deduction amount for inhabitant tax in JPY
     */
    long calculateInhabitantTaxDeduction(long totalIncome);
}

