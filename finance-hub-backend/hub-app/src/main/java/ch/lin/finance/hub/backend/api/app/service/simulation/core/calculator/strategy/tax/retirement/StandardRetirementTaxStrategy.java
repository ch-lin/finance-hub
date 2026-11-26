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
 * Standard retirement income taxation strategy for service/contribution years exceeding 5 years (勤続年数 > 5年).
 * <p>
 * Under Japanese Income Tax Law Article 30 Paragraph 2 (所得税法第30条第2項), taxable retirement income is:
 * <pre>
 *   (retirementIncome - deduction) * 1/2
 * </pre>
 * Rounded down to the nearest 1,000 yen (国税通則法第118条第1項).
 */
public class StandardRetirementTaxStrategy implements RetirementTaxStrategy {

    @Override
    public long calculateTaxableIncome(long retirementIncome, long deduction) {
        long excess = Math.max(0L, retirementIncome - deduction);
        if (excess <= 0L) {
            return 0L;
        }

        long rawTaxable = excess / 2L;
        return (rawTaxable / 1000L) * 1000L;
    }
}
