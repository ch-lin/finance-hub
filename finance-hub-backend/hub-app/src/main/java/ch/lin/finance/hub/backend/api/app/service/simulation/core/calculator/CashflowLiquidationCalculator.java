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

import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;

/**
 * Service to handle negative cashflow liquidation strategies during simulation.
 */
@Service
public class CashflowLiquidationCalculator {

    /**
     * Process negative cashflow by liquidating assets in order:
     * 1. Company Stock (ESPP)
     * 2. NISA
     * 3. Borrowing (applying interest)
     */
    public void processNegativeCashflow(SimulationContext context) {
        if (context.getCurrentLiquidCash() >= 0) {
            return;
        }

        long deficit = -context.getCurrentLiquidCash();

        // 1. Sell Company Stock first (Assuming 0% capital gains per user preference)
        long stockValue = context.getCompanyStockBalance();
        if (stockValue > 0) {
            if (stockValue >= deficit) {
                context.setCompanyStockBalance(stockValue - deficit);
                deficit = 0;
            } else {
                deficit -= stockValue;
                context.setCompanyStockBalance(0L);
            }
        }

        // 2. Sell NISA next
        if (deficit > 0) {
            long nisaValue = context.getNisaBalance();
            long actualDeficitSold = Math.min(nisaValue, deficit);

            if (actualDeficitSold > 0) {
                double sellRatio = (double) actualDeficitSold / nisaValue;
                long principalToRestore = (long) (context.getAccumulatedNisaPrincipal() * sellRatio);
                long growthPrincipalToRestore = (long) (context.getAccumulatedGrowthNisaPrincipal() * sellRatio);

                // Track the principal to be restored NEXT year
                context.setNisaPrincipalToRecoverNextYear(
                        context.getNisaPrincipalToRecoverNextYear() + principalToRestore);
                context.setGrowthNisaPrincipalToRecoverNextYear(
                        context.getGrowthNisaPrincipalToRecoverNextYear() + growthPrincipalToRestore);

                // Proportionally reduce individual funds so they don't resurrect next year
                double remainingRatio = (double) (nisaValue - actualDeficitSold) / nisaValue;
                for (ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund fund : context
                        .getNisaFunds()) {
                    long newFundBalance = Math.round(fund.getBalance() * remainingRatio);
                    fund.setBalance(newFundBalance);
                }

                context.setNisaBalance(nisaValue - actualDeficitSold);
                deficit -= actualDeficitSold;
            }
        }

        // 3. Apply Borrowing Cost
        if (deficit > 0) {
            context.setCurrentLiquidCash(-deficit);
            long borrowingInterest = Math.round(deficit * TaxConstants.NEGATIVE_CASH_BORROWING_RATE);
            context.setCurrentLiquidCash(context.getCurrentLiquidCash() - borrowingInterest);
        } else {
            context.setCurrentLiquidCash(0L);
        }
    }
}
