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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic;

import org.springframework.stereotype.Component;

/**
 * Factory for retrieving the appropriate {@link BasicDeductionStrategy} for a
 * given simulation year.
 */
@Component
public class BasicDeductionStrategyFactory {

    private final BasicDeductionStrategy legacyStrategy = new LegacyBasicDeductionStrategy();
    private final BasicDeductionStrategy reiwa7Strategy = new Reiwa7BasicDeductionStrategy();
    private final BasicDeductionStrategy reiwa8And9Strategy = new Reiwa8And9BasicDeductionStrategy();
    private final BasicDeductionStrategy reiwa10OnwardsStrategy = new Reiwa10OnwardsBasicDeductionStrategy();

    /**
     * Resolves the strategy for Income Tax (所得税，現年所得課稅).
     *
     * @param year the tax simulation year
     * @return the matching {@link BasicDeductionStrategy}
     */
    public BasicDeductionStrategy getIncomeTaxStrategy(int year) {
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

    /**
     * Resolves the strategy for Inhabitant Tax (住民税，前年所得課稅時差).
     *
     * @param year the tax simulation year
     * @return the matching {@link BasicDeductionStrategy}
     */
    public BasicDeductionStrategy getInhabitantTaxStrategy(int year) {
        if (year <= 2025) {
            return legacyStrategy;
        } else if (year == 2026) {
            return reiwa7Strategy;
        } else if (year <= 2028) {
            return reiwa8And9Strategy;
        } else {
            return reiwa10OnwardsStrategy;
        }
    }
}
