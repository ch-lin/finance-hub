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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income;

import org.springframework.stereotype.Component;

/**
 * Standard national income tax rate strategy (所得税の速算表).
 * <p>
 * Statutory basis: Japanese Income Tax Act, Article 89, Paragraph 1
 * (所得税法第89条第1項, 2015 / Heisei 27 onwards).
 * </p>
 * Progressive tax brackets (7 brackets):
 * <ul>
 *   <li>&le; 1,950,000 JPY: 5% (offset 0)</li>
 *   <li>&le; 3,300,000 JPY: 10% (offset -97,500 JPY)</li>
 *   <li>&le; 6,950,000 JPY: 20% (offset -427,500 JPY)</li>
 *   <li>&le; 9,000,000 JPY: 23% (offset -636,000 JPY)</li>
 *   <li>&le; 18,000,000 JPY: 33% (offset -1,536,000 JPY)</li>
 *   <li>&le; 40,000,000 JPY: 40% (offset -2,796,000 JPY)</li>
 *   <li>&gt; 40,000,000 JPY: 45% (offset -4,796,000 JPY)</li>
 * </ul>
 */
@Component
public class StandardIncomeTaxRateStrategy implements IncomeTaxRateStrategy {

    @Override
    public long calculateIncomeTax(long taxableIncome) {
        if (taxableIncome <= 0) {
            return 0L;
        }

        if (taxableIncome <= 1_950_000L) {
            return (long) (taxableIncome * 0.05);
        } else if (taxableIncome <= 3_300_000L) {
            return (long) (taxableIncome * 0.10) - 97_500L;
        } else if (taxableIncome <= 6_950_000L) {
            return (long) (taxableIncome * 0.20) - 427_500L;
        } else if (taxableIncome <= 9_000_000L) {
            return (long) (taxableIncome * 0.23) - 636_000L;
        } else if (taxableIncome <= 18_000_000L) {
            return (long) (taxableIncome * 0.33) - 1_536_000L;
        } else if (taxableIncome <= 40_000_000L) {
            return (long) (taxableIncome * 0.40) - 2_796_000L;
        } else {
            return (long) (taxableIncome * 0.45) - 4_796_000L;
        }
    }
}
