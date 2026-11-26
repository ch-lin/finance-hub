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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.spouse;

import org.springframework.stereotype.Component;

/**
 * Factory for retrieving the appropriate {@link SpouseDeductionStrategy} for a
 * given simulation tax year and tax category (Income Tax vs Inhabitant Tax).
 */
@Component
public class SpouseDeductionStrategyFactory {

    private final SpouseDeductionStrategy legacyStrategy = new LegacySpouseDeductionStrategy();
    private final SpouseDeductionStrategy reiwa7Strategy = new Reiwa7SpouseDeductionStrategy();
    private final SpouseDeductionStrategy reiwa8Strategy = new Reiwa8SpouseDeductionStrategy();
    private final SpouseDeductionStrategy reiwa10OnwardsStrategy = new Reiwa10OnwardsSpouseDeductionStrategy();

    /**
     * Resolves the strategy for Income Tax (所得税，現年所得課稅).
     * <ul>
     *   <li>&lt;= 2024: Legacy (令和6年分以前)</li>
     *   <li>2025: Reiwa 7 (令和7年分)</li>
     *   <li>2026..2027: Reiwa 8 (令和8~9年分特例，74萬給與所得控除)</li>
     *   <li>&gt;= 2028: Reiwa 10 Onwards (令和10年分以降本則，69萬給與所得控除)</li>
     * </ul>
     *
     * @param year the tax simulation year
     * @return the matching {@link SpouseDeductionStrategy}
     */
    public SpouseDeductionStrategy getIncomeTaxStrategy(int year) {
        if (year <= 2024) {
            return legacyStrategy;
        } else if (year == 2025) {
            return reiwa7Strategy;
        } else if (year <= 2027) {
            return reiwa8Strategy;
        } else {
            return reiwa10OnwardsStrategy;
        }
    }

    /**
     * Resolves the strategy for Personal Inhabitant Tax (住民税，前年所得課稅).
     * Assesses prior calendar year income:
     * <ul>
     *   <li>&lt;= 2025: Legacy (課徵 &lt;= 2024 年以前所得)</li>
     *   <li>2026: Reiwa 7 (課徵 2025 年 / 令和7年分所得)</li>
     *   <li>2027..2028: Reiwa 8 (課徵 2026~2027 年 / 令和8~9年分特例所得)</li>
     *   <li>&gt;= 2029: Reiwa 10 Onwards (課徵 2028 年起 / 令和10年分以降所得)</li>
     * </ul>
     *
     * @param year the tax simulation year
     * @return the matching {@link SpouseDeductionStrategy}
     */
    public SpouseDeductionStrategy getInhabitantTaxStrategy(int year) {
        if (year <= 2025) {
            return legacyStrategy;
        } else if (year == 2026) {
            return reiwa7Strategy;
        } else if (year <= 2028) {
            return reiwa8Strategy;
        } else {
            return reiwa10OnwardsStrategy;
        }
    }
}
