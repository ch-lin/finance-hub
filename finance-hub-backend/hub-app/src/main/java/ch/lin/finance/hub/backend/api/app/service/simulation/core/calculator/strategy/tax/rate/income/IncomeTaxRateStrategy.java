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

/**
 * Strategy interface for calculating national income tax (所得税)
 * based on progressive tax brackets according to statutory rules.
 */
public interface IncomeTaxRateStrategy {

    /**
     * Calculates the base income tax (基準所得税額，before reconstruction special income tax of 2.1%).
     *
     * @param taxableIncome the statutory taxable income (課税所得金額, typically rounded down to 1,000 JPY)
     * @return the calculated base national income tax
     */
    long calculateIncomeTax(long taxableIncome);
}
