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
 * Factory for resolving statutory {@link IncomeTaxRateStrategy}
 * based on simulation year.
 */
@Component
public class IncomeTaxRateStrategyFactory {

    private final IncomeTaxRateStrategy pre2015Strategy = new Pre2015IncomeTaxRateStrategy();
    private final IncomeTaxRateStrategy standardStrategy = new StandardIncomeTaxRateStrategy();

    /**
     * Resolves the income tax rate strategy for the given tax year.
     *
     * @param year the tax simulation year
     * @return the matching {@link IncomeTaxRateStrategy}
     */
    public IncomeTaxRateStrategy getStrategy(int year) {
        if (year <= 2014) {
            return pre2015Strategy;
        } else {
            return standardStrategy;
        }
    }
}
