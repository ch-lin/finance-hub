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
 * Strategy implementation for Reiwa 7 tax year (2025 income year).
 * Applies to 2025 Income Tax and 2026 Personal Inhabitant Tax (assessing 2025 income).
 * <p>
 * Statutory thresholds:
 * <ul>
 *   <li>Total income threshold: 580,000 JPY (合計所得金額)</li>
 *   <li>Wage income equivalent: 1,230,000 JPY (58萬 + 給與所得控除最低保障 65萬)</li>
 *   <li>65+ elderly pension equivalent: 1,680,000 JPY (58萬 + 公的年金等控除 110萬)</li>
 * </ul>
 */
public class Reiwa7DependentDeductionStrategy extends AbstractDependentDeductionStrategy {

    private static final long WAGE_THRESHOLD = 1_230_000L;
    private static final long TOTAL_INCOME_THRESHOLD = 580_000L;
    private static final long ELDERLY_PENSION_THRESHOLD = 1_680_000L;

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
        if (wageIncome <= 1_500_000L) {
            return 630_000L;
        } else if (wageIncome <= 1_550_000L) {
            return 610_000L;
        } else if (wageIncome <= 1_600_000L) {
            return 510_000L;
        } else if (wageIncome <= 1_650_000L) {
            return 410_000L;
        } else if (wageIncome <= 1_700_000L) {
            return 310_000L;
        } else if (wageIncome <= 1_750_000L) {
            return 210_000L;
        } else if (wageIncome <= 1_800_000L) {
            return 110_000L;
        } else if (wageIncome <= 1_850_000L) {
            return 60_000L;
        } else if (wageIncome <= 1_880_000L) {
            return 30_000L;
        } else {
            return 0L;
        }
    }

    @Override
    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
        if (wageIncome <= 1_600_000L) {
            return 450_000L;
        } else if (wageIncome <= 1_650_000L) {
            return 410_000L;
        } else if (wageIncome <= 1_700_000L) {
            return 310_000L;
        } else if (wageIncome <= 1_750_000L) {
            return 210_000L;
        } else if (wageIncome <= 1_800_000L) {
            return 110_000L;
        } else if (wageIncome <= 1_850_000L) {
            return 60_000L;
        } else if (wageIncome <= 1_880_000L) {
            return 30_000L;
        } else {
            return 0L;
        }
    }
}
