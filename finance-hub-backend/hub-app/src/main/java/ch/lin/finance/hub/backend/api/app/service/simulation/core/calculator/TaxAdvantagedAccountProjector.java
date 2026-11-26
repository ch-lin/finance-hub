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

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InvestmentContributions;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.NisaAccountType;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TaxAdvantagedAccountProjector {
    private final InvestmentReturnCalculator investmentReturnCalculator;
    private final ActiveTaxCalculationEngine taxEngine;

    public InvestmentContributions processAccountsForYear(
            SimulationContext context,
            int initialAge,
            int dcContributionMonths,
            int idecoContributionCeilingAge,
            int taxAdvantagedWithdrawalAge,
            int monthsToAdd,
            boolean isRetirementYear,
            int idecoContributionMonths) {

        int age = context.getAge();
        boolean isRetired = context.isRetired();

        long annualNisa = 0;
        for (SimulationFund fund : context.getNisaFunds()) {
            annualNisa += fund.calculateContribution(age == initialAge, monthsToAdd);
        }
        // For the transition year (age == initialAge), we intentionally calculate ONLY
        // the contributions for the "remaining months", without backfilling YTD
        // contributions.
        // Reason: DC/iDeCo providers (e.g., SBI, Sompo) only provide a current snapshot
        // and lack a month-by-month deduction history.
        // It is impossible to verify if the user has been contributing since the
        // beginning of the year/employment, or if there were interruptions or amount
        // changes mid-year (DC Matching and iDeCo might even be swapped mid-year).
        // Any approach that extrapolates a full-year baseline using "current monthly
        // contribution × elapsed months" is built on unverified assumptions.
        // If the guess is wrong, it generates "free" tax deductions that the user never
        // actually contributed, skewing both the estimated tax and cash flow in a
        // dangerously optimistic direction.
        // Maintaining the "remaining months only" approach means the transition year's
        // tax might theoretically be slightly overestimated.
        // However, this is a conservative error; the only consequence is that the user
        // will receive a larger-than-expected tax refund during the actual year-end tax
        // adjustment.
        boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);
        boolean idecoProrate = (age == initialAge) || isRetirementYear || isIdecoCeilingYear;
        int idecoMonthsForCalc = (isRetirementYear || isIdecoCeilingYear) ? idecoContributionMonths : monthsToAdd;

        long remainingMonthsIdecoContribution = 0;
        for (SimulationFund fund : context.getIdecoFunds()) {
            remainingMonthsIdecoContribution += fund.calculateContribution(idecoProrate, idecoMonthsForCalc);
        }
        boolean dcProrate = (age == initialAge) || isRetirementYear;
        int dcMonthsForCalc = isRetirementYear ? dcContributionMonths : monthsToAdd;

        long remainingMonthsDcContribution = 0;
        for (SimulationFund fund : context.getDcFunds()) {
            remainingMonthsDcContribution += fund.calculateContribution(dcProrate, dcMonthsForCalc);
        }

        // ── NISA dual-track sub-limit enforcement ───────────────────────────────────
        // Step 1: Compute raw intended contribution per sub-quota group.
        long intendedGrowth = 0L;
        long intendedTsumitate = 0L;
        for (SimulationFund fund : context.getNisaFunds()) {
            long intended = fund.calculateContribution(age == initialAge, monthsToAdd);
            if (fund.getNisaAccountType() == NisaAccountType.TSUMITATE) {
                intendedTsumitate += intended;
            } else {
                // GROWTH is the default (conservative fallback for null)
                intendedGrowth += intended;
            }
        }

        // Step 2: Apply per-type annual sub-caps independently (non-fungible).
        long growthLifetimeRemaining = TaxConstants.NISA_GROWTH_LIFETIME_LIMIT
                - context.getAccumulatedGrowthNisaPrincipal();

        long tsumitateLifetimeRemaining = TaxConstants.NISA_LIFETIME_LIMIT - context.getAccumulatedNisaPrincipal();

        long allowedGrowth = Math.max(0L, Math.min(intendedGrowth,
                Math.min(TaxConstants.NISA_GROWTH_ANNUAL_LIMIT, growthLifetimeRemaining)));
        long allowedTsumitate = Math.max(0L, Math.min(intendedTsumitate,
                Math.min(TaxConstants.NISA_TSUMITATE_ANNUAL_LIMIT, tsumitateLifetimeRemaining)));

        // Step 3: Final combined safety-net cap (should not normally trigger if
        // sub-caps
        // are configured correctly, but guards against edge cases).
        long sumAllowed = allowedGrowth + allowedTsumitate;
        long combinedAllowed = sumAllowed;
        long totalLifetimeRemaining = TaxConstants.NISA_LIFETIME_LIMIT - context.getAccumulatedNisaPrincipal();
        combinedAllowed = Math.max(0L,
                Math.min(combinedAllowed, Math.min(TaxConstants.NISA_ANNUAL_LIMIT, totalLifetimeRemaining)));

        long finalAllowedGrowth = allowedGrowth;
        long finalAllowedTsumitate = allowedTsumitate;

        if (sumAllowed > combinedAllowed) {
            finalAllowedGrowth = (allowedGrowth * combinedAllowed) / sumAllowed;
            finalAllowedTsumitate = combinedAllowed - finalAllowedGrowth;
        }

        // Preserve scale ratios per group so individual fund contributions scale
        // proportionally.
        double growthScale = intendedGrowth > 0 ? (double) finalAllowedGrowth / intendedGrowth : 0.0;
        double tsumitateScale = intendedTsumitate > 0 ? (double) finalAllowedTsumitate / intendedTsumitate : 0.0;

        // annualNisa used by InvestmentContributions return value = capped combined
        // total.
        annualNisa = combinedAllowed;
        // ────────────────────────────────────────────────────────────────────────────

        boolean dcAllowed = true;
        boolean idecoAllowed = true;

        if ((isRetired && !isRetirementYear) || age > idecoContributionCeilingAge) {
            remainingMonthsIdecoContribution = 0L;
            idecoAllowed = false;
        }
        if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge) {
            remainingMonthsDcContribution = 0L;
            dcAllowed = false;
        }

        // Progress investments
        if (age >= initialAge) {
            long newNisaBalance = 0;
            long newNisaPrincipal = context.getAccumulatedNisaPrincipal();
            long newGrowthNisaPrincipal = context.getAccumulatedGrowthNisaPrincipal();

            for (SimulationFund fund : context.getNisaFunds()) {
                long intended = fund.calculateContribution(age == initialAge, monthsToAdd);

                // Apply the correct per-type scale.
                double scale = (fund.getNisaAccountType() == NisaAccountType.TSUMITATE)
                        ? tsumitateScale
                        : growthScale;
                long annualContribution = (long) (intended * scale);

                // Update principal accumulators.
                newNisaPrincipal += annualContribution;
                if (fund.getNisaAccountType() != NisaAccountType.TSUMITATE) {
                    newGrowthNisaPrincipal += annualContribution;
                }

                Double expectedRate = fund.getExpectedReturnRate();
                double returnRate = expectedRate != null ? expectedRate : 0.0;

                if (age == initialAge) {
                    fund.setBalance(investmentReturnCalculator.projectPartialYearBalance(
                            fund.getBalance(), annualContribution, returnRate, monthsToAdd / 12.0));
                } else {
                    fund.setBalance(investmentReturnCalculator.projectInvestmentBalance(
                            fund.getBalance(), annualContribution, returnRate, 1));
                }
                newNisaBalance += fund.getBalance();
            }
            context.setNisaBalance(newNisaBalance);
            context.setAccumulatedNisaPrincipal(newNisaPrincipal);
            context.setAccumulatedGrowthNisaPrincipal(newGrowthNisaPrincipal);

            if (age <= taxAdvantagedWithdrawalAge) {
                long newDcBalance = 0;
                for (SimulationFund fund : context.getDcFunds()) {
                    long intended = fund.calculateContribution(dcProrate, dcMonthsForCalc);
                    long annualContribution = dcAllowed ? intended : 0L;
                    Double expectedRate = fund.getExpectedReturnRate();
                    double returnRate = expectedRate != null ? expectedRate : 0.0;
                    if (age == initialAge) {
                        fund.setBalance(investmentReturnCalculator.projectPartialYearBalance(
                                fund.getBalance(), annualContribution, returnRate, monthsToAdd / 12.0));
                    } else {
                        fund.setBalance(investmentReturnCalculator.projectInvestmentBalance(
                                fund.getBalance(), annualContribution, returnRate, 1));
                    }
                    newDcBalance += fund.getBalance();
                }
                context.setDcBalance(newDcBalance);
            }

            if (age <= taxAdvantagedWithdrawalAge) {
                long newIdecoBalance = 0;
                for (SimulationFund fund : context.getIdecoFunds()) {
                    long intended = fund.calculateContribution(idecoProrate, idecoMonthsForCalc);
                    long annualContribution = idecoAllowed ? intended : 0L;
                    Double expectedRate = fund.getExpectedReturnRate();
                    double returnRate = expectedRate != null ? expectedRate : 0.0;
                    if (age == initialAge) {
                        fund.setBalance(investmentReturnCalculator.projectPartialYearBalance(
                                fund.getBalance(), annualContribution, returnRate, monthsToAdd / 12.0));
                    } else {
                        fund.setBalance(investmentReturnCalculator.projectInvestmentBalance(
                                fund.getBalance(), annualContribution, returnRate, 1));
                    }
                    newIdecoBalance += fund.getBalance();
                }
                context.setIdecoBalance(newIdecoBalance);
            }
        }

        // Handle Unlocks with Retirement Tax
        boolean dcUnlocked = (age == taxAdvantagedWithdrawalAge)
                || (age == initialAge && age > taxAdvantagedWithdrawalAge);
        boolean idecoUnlocked = dcUnlocked; // Both are unlocked at the unified withdrawal age

        if (dcUnlocked || idecoUnlocked) {
            long dcNet = 0;
            long idecoNet = 0;

            if (dcUnlocked && idecoUnlocked) {
                long totalBalance = context.getDcBalance() + context.getIdecoBalance();
                int totalYears = Math.max(context.getDcContributionYears(), context.getIdecoContributionYears());
                long totalNet = taxEngine.calculateRetirementTax(context, totalBalance, totalYears).netIncome();

                if (totalBalance > 0) {
                    dcNet = (long) (totalNet * ((double) context.getDcBalance() / totalBalance));
                    idecoNet = totalNet - dcNet;
                }
            } else {
                if (dcUnlocked) {
                    dcNet = taxEngine
                            .calculateRetirementTax(context, context.getDcBalance(), context.getDcContributionYears())
                            .netIncome();
                }
                if (idecoUnlocked) {
                    idecoNet = taxEngine
                            .calculateRetirementTax(context, context.getIdecoBalance(),
                                    context.getIdecoContributionYears())
                            .netIncome();
                }
            }

            context.setCurrentLiquidCash(context.getCurrentLiquidCash() + dcNet + idecoNet);

            if (dcUnlocked) {
                context.setDcBalance(0);
                context.getDcFunds().forEach(f -> f.setBalance(0));
            }
            if (idecoUnlocked) {
                context.setIdecoBalance(0);
                context.getIdecoFunds().forEach(f -> f.setBalance(0));
            }
        }

        long annualEmployerContribution = context.getDcMonthlyEmployerContribution() * dcMonthsForCalc;

        long annualDcMatching = Math.max(0, remainingMonthsDcContribution - annualEmployerContribution);

        return new InvestmentContributions(annualNisa, remainingMonthsIdecoContribution, remainingMonthsDcContribution,
                annualDcMatching);
    }
}
