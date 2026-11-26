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
 * Strategy implementation for Reiwa 8 and Reiwa 9 tax years (2026-2027 income years).
 * Incorporates the 2-year temporary special measure (時限特例) raising employment income deduction
 * minimum guarantee to 740,000 JPY (690,000 JPY permanent + 50,000 JPY temporary special deduction).
 * <p>
 * Statutory thresholds:
 * <ul>
 *   <li>Total income threshold: 620,000 JPY (合計所得金額)</li>
 *   <li>Wage income equivalent: 1,360,000 JPY (62萬 + 給與所得控除最低保障 74萬 [69萬永久 + 5萬特例])</li>
 *   <li>65+ elderly pension equivalent: 1,720,000 JPY (62萬 + 公的年金等控除 110萬)</li>
 * </ul>
 */
public class Reiwa8And9DependentDeductionStrategy extends AbstractDependentDeductionStrategy {

    private static final long WAGE_THRESHOLD = 1_360_000L;
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
        if (wageIncome <= 1_590_000L) {
            return 630_000L;
        } else if (wageIncome <= 1_640_000L) {
            return 610_000L;
        } else if (wageIncome <= 1_690_000L) {
            return 510_000L;
        } else if (wageIncome <= 1_740_000L) {
            return 410_000L;
        } else if (wageIncome <= 1_790_000L) {
            return 310_000L;
        } else if (wageIncome <= 1_840_000L) {
            return 210_000L;
        } else if (wageIncome <= 1_890_000L) {
            return 110_000L;
        } else if (wageIncome <= 1_940_000L) {
            return 60_000L;
        } else if (wageIncome <= 1_970_000L) {
            return 30_000L;
        } else {
            return 0L;
        }
    }

    @Override
    protected long calculateSpecificDependentSpecialInhabitantTaxDeduction(long wageIncome) {
        if (wageIncome <= 1_690_000L) {
            return 450_000L;
        } else if (wageIncome <= 1_740_000L) {
            return 410_000L;
        } else if (wageIncome <= 1_790_000L) {
            return 310_000L;
        } else if (wageIncome <= 1_840_000L) {
            return 210_000L;
        } else if (wageIncome <= 1_890_000L) {
            return 110_000L;
        } else if (wageIncome <= 1_940_000L) {
            return 60_000L;
        } else if (wageIncome <= 1_970_000L) {
            return 30_000L;
        } else {
            return 0L;
        }
    }
}
