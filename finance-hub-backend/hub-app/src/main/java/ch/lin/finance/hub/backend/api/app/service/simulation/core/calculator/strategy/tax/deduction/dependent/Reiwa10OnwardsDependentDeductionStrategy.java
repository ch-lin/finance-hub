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

/**
 * Strategy implementation for Reiwa 10 tax year and onwards (>= 2028 income years).
 * Reflects the sunset of the 50,000 JPY temporary wage deduction bonus, reverting the minimum
 * employment income deduction guarantee to the permanent baseline of 690,000 JPY.
 * <p>
 * Statutory thresholds:
 * <ul>
 *   <li>Total income threshold: 620,000 JPY (合計所得金額)</li>
 *   <li>Wage income equivalent: 1,310,000 JPY (62萬 + 給與所得控除永久本則 69萬)</li>
 *   <li>65+ elderly pension equivalent: 1,720,000 JPY (62萬 + 公的年金等控除 110萬)</li>
 * </ul>
 */
public class Reiwa10OnwardsDependentDeductionStrategy extends AbstractDependentDeductionStrategy {

    private static final long WAGE_THRESHOLD = 1_310_000L;
    private static final long TOTAL_INCOME_THRESHOLD = 620_000L;
    private static final long ELDERLY_PENSION_THRESHOLD = 1_720_000L;

    @Override
    public long getWageIncomeThreshold() {
        return WAGE_THRESHOLD;
    }

    @Override
    public long getTotalIncomeThreshold() {
        return TOTAL_INCOME_THRESHOLD;
    }

    @Override
    public long getElderlyPensionThreshold() {
        return ELDERLY_PENSION_THRESHOLD;
    }

    @Override
    protected long calculateSpecificDependentSpecialIncomeTaxDeduction(long wageIncome) {
        if (wageIncome <= 1_540_000L) {
            return 630_000L;
        } else if (wageIncome <= 1_590_000L) {
            return 610_000L;
        } else if (wageIncome <= 1_640_000L) {
            return 510_000L;
        } else if (wageIncome <= 1_690_000L) {
            return 410_000L;
        } else if (wageIncome <= 1_740_000L) {
            return 310_000L;
        } else if (wageIncome <= 1_790_000L) {
            return 210_000L;
        } else if (wageIncome <= 1_840_000L) {
            return 110_000L;
        } else if (wageIncome <= 1_890_000L) {
            return 60_000L;
        } else if (wageIncome <= 1_920_000L) {
            return 30_000L;
        } else {
            return 0L;
        }
    }

    @Override
    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
        if (wageIncome <= 1_640_000L) {
            return 450_000L;
        } else if (wageIncome <= 1_690_000L) {
            return 410_000L;
        } else if (wageIncome <= 1_740_000L) {
            return 310_000L;
        } else if (wageIncome <= 1_790_000L) {
            return 210_000L;
        } else if (wageIncome <= 1_840_000L) {
            return 110_000L;
        } else if (wageIncome <= 1_890_000L) {
            return 60_000L;
        } else if (wageIncome <= 1_920_000L) {
            return 30_000L;
        } else {
            return 0L;
        }
    }
}
