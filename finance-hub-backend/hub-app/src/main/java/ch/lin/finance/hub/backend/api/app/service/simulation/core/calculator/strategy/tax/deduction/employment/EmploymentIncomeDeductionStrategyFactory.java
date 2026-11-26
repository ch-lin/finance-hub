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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment;

import org.springframework.stereotype.Component;

/**
 * Factory for resolving statutory {@link EmploymentIncomeDeductionStrategy}
 * based on simulation year.
 */
@Component
public class EmploymentIncomeDeductionStrategyFactory {

    private final EmploymentIncomeDeductionStrategy legacyStrategy = new LegacyEmploymentIncomeDeductionStrategy();
    private final EmploymentIncomeDeductionStrategy reiwa7Strategy = new Reiwa7EmploymentIncomeDeductionStrategy();
    private final EmploymentIncomeDeductionStrategy reiwa8And9Strategy = new Reiwa8And9EmploymentIncomeDeductionStrategy();
    private final EmploymentIncomeDeductionStrategy reiwa10OnwardsStrategy = new Reiwa10OnwardsEmploymentIncomeDeductionStrategy();

    /**
     * Resolves the employment income deduction strategy for the given tax year.
     *
     * @param year the tax simulation year
     * @return the matching {@link EmploymentIncomeDeductionStrategy}
     */
    public EmploymentIncomeDeductionStrategy getStrategy(int year) {
        if (year <= 2024) {
            return legacyStrategy;
        } else if (year == 2025) {
            return reiwa7Strategy;
        } else if (year <= 2027) {
            return reiwa8And9Strategy;
        } else {
            return reiwa10OnwardsStrategy;
        }
    }
}
