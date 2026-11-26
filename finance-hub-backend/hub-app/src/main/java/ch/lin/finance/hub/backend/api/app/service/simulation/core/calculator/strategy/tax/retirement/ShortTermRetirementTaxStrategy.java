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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement;

/**
 * Short-term retirement income taxation strategy for service/contribution years of 5 years or less (勤続年数 5年以下).
 * <p>
 * Under Japanese Income Tax Law Article 30 Paragraph 4 (所得税法第30条第4項, effective from January 1, 2022)
 * and Local Tax Law (地方税法第50条の3第2項・第328条の2第2項), for short-term retirement allowances (短期退職手当等):
 * <ul>
 *   <li>Excess after deduction &le; 3,000,000 yen: <code>excess / 2</code></li>
 *   <li>Excess after deduction &gt; 3,000,000 yen: <code>1,500,000 + (excess - 3,000,000)</code></li>
 * </ul>
 * The resulting taxable amount is rounded down to the nearest 1,000 yen (国税通則法第118条第1項).
 */
public class ShortTermRetirementTaxStrategy implements RetirementTaxStrategy {

    private static final long HALF_TAXATION_THRESHOLD = 3_000_000L;
    private static final long HALF_TAXATION_BASE = 1_500_000L;

    @Override
    public long calculateTaxableIncome(long retirementIncome, long deduction) {
        long excess = Math.max(0L, retirementIncome - deduction);
        if (excess <= 0L) {
            return 0L;
        }

        long rawTaxable;
        if (excess <= HALF_TAXATION_THRESHOLD) {
            rawTaxable = excess / 2L;
        } else {
            rawTaxable = HALF_TAXATION_BASE + (excess - HALF_TAXATION_THRESHOLD);
        }

        return (rawTaxable / 1000L) * 1000L;
    }
}
