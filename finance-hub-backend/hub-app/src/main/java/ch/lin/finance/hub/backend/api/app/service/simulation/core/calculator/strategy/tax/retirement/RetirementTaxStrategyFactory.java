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

import org.springframework.stereotype.Component;

/**
 * Factory for resolving the appropriate {@link RetirementTaxStrategy} based on years of service or contribution years.
 */
@Component
public class RetirementTaxStrategyFactory {

    private final RetirementTaxStrategy standardStrategy = new StandardRetirementTaxStrategy();
    private final RetirementTaxStrategy shortTermStrategy = new ShortTermRetirementTaxStrategy();

    /**
     * Resolves the strategy based on contribution/service years (掛金拠出年数 / 勤続年数).
     *
     * @param contributionYears years of contribution or service
     * @return {@link ShortTermRetirementTaxStrategy} if {@code contributionYears <= 5};
     *         {@link StandardRetirementTaxStrategy} if {@code contributionYears > 5}
     */
    public RetirementTaxStrategy getStrategy(int contributionYears) {
        if (contributionYears <= 5) {
            return shortTermStrategy;
        }
        return standardStrategy;
    }
}
