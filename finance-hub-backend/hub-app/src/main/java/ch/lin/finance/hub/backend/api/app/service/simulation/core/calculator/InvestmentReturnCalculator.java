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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator;

import org.springframework.stereotype.Component;

/**
 * Pure math utility for projecting investment balances over time.
 *
 * <p>
 * This component is intentionally kept free of DB access and override logic.
 * It only knows the DCA compounding formula. Override resolution and fund
 * initialization are the responsibility of {@link FundOverrideApplier} and
 * {@code AssetSimulationEngineImpl.initializeContext()} respectively.
 */
@Component
public class InvestmentReturnCalculator {

    /**
     * Projects the future balance of an investment using the DCA approximation
     * formula.
     *
     * <p>
     * The formula applies a full year's return to the initial balance and half a
     * year's return to the new contribution (mid-year convention), then repeats for
     * {@code yearsOffset} iterations.
     *
     * @param initialBalance     the balance at the start of the projection period
     * @param annualContribution the fixed amount contributed each year
     * @param annualRate         the annual expected return rate (e.g. 0.07 for 7%)
     * @param yearsOffset        the number of years to project forward
     * @return the projected balance after the specified number of years
     */
    public long projectInvestmentBalance(long initialBalance, long annualContribution, double annualRate,
            int yearsOffset) {
        if (annualRate == 0) {
            return initialBalance + (annualContribution * yearsOffset);
        }

        long balance = initialBalance;
        for (int i = 0; i < yearsOffset; i++) {
            balance = Math.round(balance * (1.0 + annualRate) + annualContribution * (1.0 + (annualRate / 2.0)));
        }
        return balance;
    }

    /**
     * Projects the future balance for a partial year using a proportional approximation formula.
     * 
     * @param initialBalance     the balance at the start of the partial period
     * @param contribution       the total amount contributed during the partial period
     * @param annualRate         the annual expected return rate
     * @param remainingYearRatio the fraction of the year remaining (e.g., 0.5 for 6 months)
     * @return the projected balance at the end of the partial year
     */
    public long projectPartialYearBalance(long initialBalance, long contribution, double annualRate, double remainingYearRatio) {
        if (annualRate == 0 || remainingYearRatio == 0) {
            return initialBalance + contribution;
        }
        
        long projectedOldBalance = Math.round(initialBalance * (1.0 + annualRate * remainingYearRatio));
        long projectedNewContribution = Math.round(contribution * (1.0 + annualRate * (remainingYearRatio / 2.0)));
        
        return projectedOldBalance + projectedNewContribution;
    }
}
