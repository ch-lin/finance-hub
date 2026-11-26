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
 * Strategy implementation for Reiwa 6 and earlier tax years (<= 2024).
 * <p>
 * Statutory thresholds:
 * <ul>
 *   <li>Total income threshold: 480,000 JPY (合計所得金額)</li>
 *   <li>Wage income equivalent: 1,030,000 JPY (48萬 + 給與所得控除 55萬，著名的「103萬之壁」)</li>
 *   <li>65+ elderly pension equivalent: 1,580,000 JPY (48萬 + 公的年金等控除 110萬)</li>
 * </ul>
 */
public class LegacyDependentDeductionStrategy extends AbstractDependentDeductionStrategy {

    private static final long WAGE_THRESHOLD = 1_030_000L;
    private static final long TOTAL_INCOME_THRESHOLD = 480_000L;
    private static final long ELDERLY_PENSION_THRESHOLD = 1_580_000L;

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
}
