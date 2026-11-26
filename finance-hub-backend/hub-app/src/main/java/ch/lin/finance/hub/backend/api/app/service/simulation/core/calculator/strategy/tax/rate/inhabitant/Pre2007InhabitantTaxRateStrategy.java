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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant;

import org.springframework.stereotype.Component;

/**
 * Pre-2007 individual inhabitant tax rate strategy (旧制 3段階累進税率).
 * <p>
 * Statutory basis: Japanese Local Tax Act prior to the 2007 Trinity Reform
 * (平成18年以前 / &le; 2006).
 * </p>
 * Progressive tax brackets (3 brackets, 5% ~ 13%):
 * <ul>
 *   <li>&le; 2,000,000 JPY: 5% (offset 0)</li>
 *   <li>&le; 7,000,000 JPY: 10% (offset -100,000 JPY)</li>
 *   <li>&gt; 7,000,000 JPY: 13% (offset -310,000 JPY)</li>
 * </ul>
 */
@Component
public class Pre2007InhabitantTaxRateStrategy implements InhabitantTaxRateStrategy {

    /**
     * Non-taxable threshold for total income under Local Tax Act Article 295, Paragraph 1, Item 2
     * (地方税法第295条第1項第2号 均等割非課税限度額 450,000 JPY for single taxpayer in Grade 1 areas).
     */
    private static final long FLAT_LEVY_THRESHOLD = 450_000L;

    /**
     * Standard per-capita levy prior to Trinity Reform (均等割 4,000 JPY under Local Tax Act Articles 38 and 310;
     * no Forest Environmental Tax or Reconstruction Special Tax prior to 2007).
     */
    private static final long FLAT_LEVY_AMOUNT = 4_000L;

    @Override
    public long calculateInhabitantTax(long taxableIncome, long totalIncome) {
        long flatLevy = (totalIncome > FLAT_LEVY_THRESHOLD) ? FLAT_LEVY_AMOUNT : 0L;
        if (taxableIncome <= 0) {
            return flatLevy;
        }

        long incomeLevy;
        if (taxableIncome <= 2_000_000L) {
            incomeLevy = (long) (taxableIncome * 0.05);
        } else if (taxableIncome <= 7_000_000L) {
            incomeLevy = (long) (taxableIncome * 0.10) - 100_000L;
        } else {
            incomeLevy = (long) (taxableIncome * 0.13) - 310_000L;
        }

        return incomeLevy + flatLevy;
    }
}
