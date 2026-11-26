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
 * Standard individual inhabitant tax rate strategy (標準税率 10%).
 * <p>
 * Statutory basis: Japanese Local Tax Act, Article 35 (Prefectural tax 4%)
 * and Article 314-3 (Municipal tax 6%) (地方税法第35条及び第314条の3, 2007 / Heisei 19 onwards).
 * Flat 10% rate nationwide.
 * </p>
 */
@Component
public class StandardInhabitantTaxRateStrategy implements InhabitantTaxRateStrategy {

    /**
     * Non-taxable threshold for total income under Local Tax Act Article 295, Paragraph 1, Item 2
     * (地方税法第295条第1項第2号 均等割非課税限度額 450,000 JPY for single taxpayer in Grade 1 areas).
     */
    private static final long FLAT_LEVY_THRESHOLD = 450_000L;

    /**
     * Standard per-capita levy (均等割 4,000 JPY under Local Tax Act Articles 38 and 310)
     * plus national Forest Environmental Tax (森林環境税 1,000 JPY under Forest Environmental Tax Act Article 4).
     */
    private static final long FLAT_LEVY_AMOUNT = 5_000L;

    @Override
    public long calculateInhabitantTax(long taxableIncome, long totalIncome) {
        long flatLevy = (totalIncome > FLAT_LEVY_THRESHOLD) ? FLAT_LEVY_AMOUNT : 0L;
        if (taxableIncome <= 0) {
            return flatLevy;
        }
        return (long) (taxableIncome * 0.10) + flatLevy;
    }
}
