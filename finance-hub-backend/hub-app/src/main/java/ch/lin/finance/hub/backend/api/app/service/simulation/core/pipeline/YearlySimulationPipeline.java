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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.CashflowLiquidationCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InvestmentContributions;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.RetirementMonthResolver;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.TaxDateUtils;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YearlySimulationPipeline {

    private final IncomeExpenseCalculator incomeExpenseCalculator;
    private final MortgageCalculator mortgageSimulationService;
    private final CashflowLiquidationCalculator liquidationSimulationService;
    private final PensionCalculator pensionCalculator;
    private final TaxAdvantagedAccountProjector taxAdvantagedAccountProjector;
    private final ActiveTaxCalculationEngine taxEngine;
    private final YearEndAdjustmentEngine yearEndAdjustmentEngine;

    public AssetSimulationYearlyResult processSimulationYear(SimulationContext context, SimulationOverrides overrides,
            List<CareerEvent> careerEvents, List<LifeEvent> lifeEvents, List<MortgageRecord> allMortgages,
            YearMonth nowMonth, int dcUnlockAge, int idecoUnlockAge, int taxAdvantagedWithdrawalAge,
            int targetRetirementAge, int pensionStartAge, int initialAge, int age) {
        context.setAge(age);
        context.setYear(context.getCurrentYear() + (age - initialAge));
        context.setRetired(age >= targetRetirementAge);

        // Restore NISA principal from previous year's liquidation
        restoreNisaPrincipal(context);

        // Read the prior year's settled tax record (capped Kakutei Shinkoku refund and
        // inhabitant tax rollover credit)
        PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
        if (prevTax != null) {
            long refundPayout = 0L;
            if (prevTax.pendingKakuteiShinkokuRefund() > 0) {
                refundPayout += prevTax.pendingKakuteiShinkokuRefund();
            }
            if (prevTax.pendingWorkingSegmentTaxAdjustment() != 0) {
                refundPayout += prevTax.pendingWorkingSegmentTaxAdjustment();
            }
            if (refundPayout != 0) {
                context.setCurrentLiquidCash(context.getCurrentLiquidCash() + refundPayout);
            }
            // NOTE: Only zero out pendingKakuteiShinkokuRefund (0L) and
            // pendingWorkingSegmentTaxAdjustment (0L)
            // upon cash disbursement to prevent duplicate payout.
            // DO NOT set context.setPreviousYearTaxRecord(null) here:
            // We MUST preserve unusedMortgageDeductionForInhabitantTax in context so that
            // ActiveTaxCalculationEngine can consume it later in the annual pipeline for
            // inhabitant tax deduction.
            context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                    0L, // Refund disbursed; zeroed to prevent double redemption
                    prevTax.unusedMortgageDeductionForInhabitantTax(),
                    0L // Adjustment disbursed; zeroed to prevent double redemption
            ));
        }

        boolean isRetirementYear = (age == targetRetirementAge);
        LocalDate birthDate = (context.getProfile() != null) ? context.getProfile().getBirthDate() : null;
        RetirementMonthMode mode = (context.getConfig() != null) ? context.getConfig().getRetirementMonthMode() : null;
        Integer retirementMonth = isRetirementYear ? RetirementMonthResolver.resolveRetirementMonth(birthDate, mode)
                : null;

        int monthsToAdd = 12;
        int currentMonth = nowMonth.getMonthValue();
        if (age == initialAge) {
            monthsToAdd = 12 - currentMonth + 1;
        }

        long annualizedSalaryRate;
        long effectiveAnnualSalary;

        if (isRetirementYear && retirementMonth == null) {
            // birthDate missing under BIRTH_MONTH mode: fallback to full-year retired (0
            // working months)
            annualizedSalaryRate = 0L;
            effectiveAnnualSalary = 0L;
        } else if (isRetirementYear && retirementMonth != null) {
            // Split retirement year (worked retirementMonth months)
            if (age == initialAge) {
                if (retirementMonth < currentMonth) {
                    // Already retired prior to current month; salary already in bank deposits/YTD
                    effectiveAnnualSalary = 0L;
                    annualizedSalaryRate = 0L;
                } else {
                    int salaryStartMonth = context.isCurrentMonthSalaryRecorded()
                            ? currentMonth + 1
                            : currentMonth;
                    effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYearUntilMonth(
                            context, careerEvents, context.getYtdBaseSalary(), salaryStartMonth, retirementMonth);
                    annualizedSalaryRate = context.getCurrentAnnualSalary();
                }
            } else {
                effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYearUntilMonth(
                        context, careerEvents, null, 1, retirementMonth);
                annualizedSalaryRate = context.getCurrentAnnualSalary();
            }
        } else {
            if (age == initialAge) {
                int salaryMonthsToAdd = context.isCurrentMonthSalaryRecorded()
                        ? 12 - currentMonth
                        : monthsToAdd;
                effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
                        context, careerEvents, context.getYtdBaseSalary(), salaryMonthsToAdd);
                annualizedSalaryRate = context.getCurrentAnnualSalary();
            } else {
                annualizedSalaryRate = incomeExpenseCalculator.calculateSalaryForYear(context, careerEvents);
                effectiveAnnualSalary = annualizedSalaryRate;
            }
        }
        long annualExpenses = incomeExpenseCalculator.calculateExpensesForYear(context, lifeEvents, monthsToAdd,
                currentMonth);

        MortgageYearlyResult mortgageResult = mortgageSimulationService.simulateMortgagesForYear(context,
                allMortgages, nowMonth, overrides);

        Integer userDeclaredWithdrawalAge = (context.getProfile() != null)
                ? context.getProfile().getTaxAdvantagedWithdrawalAge()
                : null;
        int idecoContributionCeilingAge = (userDeclaredWithdrawalAge != null)
                ? Math.min(userDeclaredWithdrawalAge, 65)
                : 65;

        int idecoContributionMonths = monthsToAdd;
        if (isRetirementYear) {
            if (retirementMonth == null) {
                idecoContributionMonths = 0;
            } else if (age == initialAge) {
                idecoContributionMonths = Math.max(0, retirementMonth - currentMonth + 1);
            } else {
                idecoContributionMonths = retirementMonth;
            }
        }
        boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);
        if (isIdecoCeilingYear) {
            Integer birthMonth = (birthDate != null) ? birthDate.getMonthValue() : null;
            if (birthMonth == null) {
                idecoContributionMonths = 0;
            } else {
                int cappedMonth = (isRetirementYear && retirementMonth != null)
                        ? Math.min(birthMonth, retirementMonth)
                        : birthMonth;
                idecoContributionMonths = (age == initialAge)
                        ? Math.max(0, cappedMonth - currentMonth + 1)
                        : cappedMonth;
            }
        }

        int dcContributionMonths = monthsToAdd;
        if (isRetirementYear) {
            if (retirementMonth == null) {
                dcContributionMonths = 0;
            } else if (age == initialAge) {
                dcContributionMonths = Math.max(0, retirementMonth - currentMonth + 1);
            } else {
                dcContributionMonths = retirementMonth;
            }
        }

        InvestmentContributions contributions = taxAdvantagedAccountProjector.processAccountsForYear(
                context, initialAge, dcContributionMonths, idecoContributionCeilingAge, taxAdvantagedWithdrawalAge,
                monthsToAdd, isRetirementYear, idecoContributionMonths);

        YearlySimulationState state = new YearlySimulationState();
        state.setAnnualExpenses(annualExpenses);

        return calculateTaxesAndBuildResult(context, pensionStartAge, initialAge, age, monthsToAdd,
                annualizedSalaryRate, effectiveAnnualSalary, state, mortgageResult, contributions,
                isRetirementYear, retirementMonth);
    }

    private AssetSimulationYearlyResult calculateTaxesAndBuildResult(SimulationContext context,
            int pensionStartAge, int initialAge, int age, int monthsToAdd,
            long annualizedSalaryRate, long effectiveAnnualSalary, YearlySimulationState state,
            MortgageYearlyResult mortgageResult, InvestmentContributions contributions,
            boolean isRetirementYear, Integer retirementMonth) {

        int workingMonths = monthsToAdd;
        int pensionMonths = monthsToAdd;
        boolean isHandlingPreRetirementSegment = false;
        boolean isHandlingPostRetirementSegment = false;

        if (isRetirementYear) {
            workingMonths = (retirementMonth != null) ? retirementMonth : 0;
            pensionMonths = 12 - workingMonths;
            isHandlingPreRetirementSegment = true;
            isHandlingPostRetirementSegment = true;
        }

        applyWorkingTaxes(context, state, age, initialAge, workingMonths, annualizedSalaryRate, effectiveAnnualSalary,
                mortgageResult, contributions, isHandlingPreRetirementSegment);
        log.debug("Age {} [After Working Taxes]: {}", age, state);

        applyRetireeTaxes(context, state, age, initialAge, monthsToAdd, pensionStartAge,
                isHandlingPostRetirementSegment, pensionMonths, mortgageResult);
        log.debug("Age {} [After Retiree Taxes]: {}", age, state);

        applyNationalInsuranceOutOfPocket(context, state, age, effectiveAnnualSalary, monthsToAdd);
        log.debug("Age {} [After National Insurance]: {}", age, state);

        if (isRetirementYear && workingMonths < 12) {
            // Mid-year retirement: no employer at year end to perform Year-End Adjustment
        } else {
            applyYearEndAdjustment(context, state, age, initialAge, effectiveAnnualSalary);
            log.debug("Age {} [After Year End Adj]: {}", age, state);
        }

        return applyCashflowAndSettlement(context, effectiveAnnualSalary, state, mortgageResult,
                contributions);
    }

    private void applyWorkingTaxes(SimulationContext context, YearlySimulationState state, int age, int initialAge,
            int monthsToAdd, long annualizedSalaryRate, long effectiveAnnualSalary, MortgageYearlyResult mortgageResult,
            InvestmentContributions contributions, boolean isHandlingPreRetirementSegment) {
        if ((!context.isRetired() || isHandlingPreRetirementSegment) && effectiveAnnualSalary > 0) {
            long kouseiContribution = pensionCalculator.calculateYearlyKouseiBenefitIncrement(
                    context.getPensionConfig(), annualizedSalaryRate, context.getCurrentAnnualBonus(),
                    context.getCurrentBonusPaymentCount());

            // For Year 1 or pre-retirement segment, proportionally accumulate Kousei
            // pension benefit
            if (age == initialAge || isHandlingPreRetirementSegment) {
                double remainingRatio = monthsToAdd / 12.0;
                kouseiContribution = Math.round(kouseiContribution * remainingRatio);
            }
            state.setKouseiContribution(kouseiContribution);

            context.setAccumulatedKousei(context.getAccumulatedKousei() + kouseiContribution);
            context.setCurrentKouseiMonths(context.getCurrentKouseiMonths() + monthsToAdd);

            if (context.getDcEnrollmentDate() != null) {
                if (contributions.annualDcTotalContribution() > 0) {
                    context.setDcContributionYears(
                            TaxDateUtils.calculateTaxServiceYears(context.getDcEnrollmentDate(),
                                    LocalDate.of(context.getYear(), 12, 31)));
                }
            } else if (contributions.annualDcTotalContribution() > 0) {
                context.setDcContributionYears(context.getDcContributionYears() + 1);
            }

            if (context.getIdecoEnrollmentDate() != null) {
                if (contributions.annualIdecoContribution() > 0) {
                    context.setIdecoContributionYears(
                            TaxDateUtils.calculateTaxServiceYears(context.getIdecoEnrollmentDate(),
                                    LocalDate.of(context.getYear(), 12, 31)));
                }
            } else if (contributions.annualIdecoContribution() > 0) {
                context.setIdecoContributionYears(context.getIdecoContributionYears() + 1);
            }

            var taxResult = taxEngine.calculate(
                    context,
                    effectiveAnnualSalary,
                    context.getCurrentAnnualBonus(),
                    context.getCurrentBonusPaymentCount(),
                    contributions.annualIdecoContribution(),
                    contributions.annualDcMatchingContribution(),
                    mortgageResult);
            state.setEstimatedIncomeTax(state.getEstimatedIncomeTax() + taxResult.incomeTax());
            state.setEstimatedInhabitantTax(state.getEstimatedInhabitantTax() + taxResult.inhabitantTax());
            state.setEstimatedSocialInsurance(state.getEstimatedSocialInsurance() + taxResult.socialInsurance());
            state.setNetIncome(state.getNetIncome() + taxResult.netIncome());

            // Initial transition year mid-year retirement: calculate working segment YTD
            // difference and update PreviousYearTaxRecord
            if (age == initialAge && isHandlingPreRetirementSegment && monthsToAdd < 12) {
                long ytdSocialInsurance = context.getYtdHealthInsurance() + context.getYtdPension()
                        + context.getYtdEmploymentInsurance();
                long totalAdjustment = (context.getYtdIncomeTax() - taxResult.incomeTax())
                        + (context.getYtdResidentTax() - taxResult.inhabitantTax())
                        + (ytdSocialInsurance - taxResult.socialInsurance());

                PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
                if (prevTax != null) {
                    context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                            prevTax.pendingKakuteiShinkokuRefund(),
                            prevTax.unusedMortgageDeductionForInhabitantTax(),
                            totalAdjustment));
                }
            }
        }
    }

    private void applyRetireeTaxes(SimulationContext context, YearlySimulationState state, int age, int initialAge,
            int monthsToAdd, int pensionStartAge, boolean isHandlingPostRetirementSegment, int pensionMonths,
            MortgageYearlyResult mortgageResult) {
        if (context.isRetired()) {
            if (isHandlingPostRetirementSegment && pensionMonths == 0) {
                return; // DECEMBER mode: worked 12 months in-job, 0 months pension
            }

            long basicPension = 0;
            long kouseiPension = 0;
            int validPensionStartAge = pensionCalculator.getValidPensionStartAge(pensionStartAge);
            if (age >= validPensionStartAge) {
                // Calculate basic pension with 0 expected additional months since they are
                // already retired
                basicPension = pensionCalculator.calculateProjectedBasicPension(
                        context.getPensionConfig(),
                        context.getCurrentKokuminMonths(),
                        context.getCurrentKouseiMonths(),
                        0, pensionStartAge);
                kouseiPension = Math.round(context.getAccumulatedKousei()
                        * pensionCalculator.calculatePensionMultiplier(pensionStartAge));
            }
            long pensionIncome = basicPension + kouseiPension;

            var taxResult = taxEngine.calculatePensionTax(context, pensionIncome, age, mortgageResult);

            double remainingRatio = 1.0;
            boolean shouldScale = false;
            if (isHandlingPostRetirementSegment && pensionMonths < 12) {
                remainingRatio = (double) pensionMonths / 12.0;
                shouldScale = true;
            } else if (age == initialAge && monthsToAdd < 12) {
                remainingRatio = (double) monthsToAdd / 12.0;
                shouldScale = true;
            }

            long estPensionIncome = shouldScale ? Math.round(pensionIncome * remainingRatio) : pensionIncome;
            long estIncomeTax = shouldScale ? Math.round(taxResult.incomeTax() * remainingRatio)
                    : taxResult.incomeTax();
            long estInhabitantTax = shouldScale ? Math.round(taxResult.inhabitantTax() * remainingRatio)
                    : taxResult.inhabitantTax();
            long estSocialInsurance = shouldScale ? Math.round(taxResult.socialInsurance() * remainingRatio)
                    : taxResult.socialInsurance();
            long estNetIncome = shouldScale ? Math.round(taxResult.netIncome() * remainingRatio)
                    : taxResult.netIncome();

            state.setPensionIncome(estPensionIncome);
            state.setEstimatedIncomeTax(state.getEstimatedIncomeTax() + estIncomeTax);
            state.setEstimatedInhabitantTax(state.getEstimatedInhabitantTax() + estInhabitantTax);
            state.setEstimatedSocialInsurance(state.getEstimatedSocialInsurance() + estSocialInsurance);
            state.setNetIncome(state.getNetIncome() + estNetIncome);
        }
    }

    private void applyNationalInsuranceOutOfPocket(SimulationContext context, YearlySimulationState state, int age,
            long effectiveAnnualSalary, int monthsToAdd) {
        if ((context.isRetired() || effectiveAnnualSalary == 0) && age >= 20 && age < 60) {
            // If not working (retired or 0 salary) but under 60 in Japan, still need to pay
            // Kokumin Nenkin out of pocket
            context.setCurrentKokuminMonths(context.getCurrentKokuminMonths() + monthsToAdd);
            long annualExpenses = state.getAnnualExpenses();
            // Standard Kokumin Nenkin premium exact months
            annualExpenses += (TaxConstants.KOKUMIN_NENKIN_MONTHLY_PREMIUM * monthsToAdd);
            state.setAnnualExpenses(annualExpenses);
        }

        if ((context.isRetired() || effectiveAnnualSalary == 0) && age >= 20 && age < 75
                && state.getPensionIncome() == 0) {
            // In addition to pension, early retirees or non-working individuals must pay
            // National Health Insurance.
            long healthPremium = TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM;
            if (monthsToAdd < 12) {
                healthPremium = Math.round((healthPremium / 12.0) * monthsToAdd);
            }
            long annualExpenses = state.getAnnualExpenses();
            annualExpenses += healthPremium;
            state.setAnnualExpenses(annualExpenses);
        }
    }

    private void applyYearEndAdjustment(SimulationContext context, YearlySimulationState state, int age, int initialAge,
            long effectiveAnnualSalary) {
        if (age == initialAge) {
            long annualGross = effectiveAnnualSalary + context.getCurrentAnnualBonus();
            long ytdSocialInsurance = context.getYtdHealthInsurance() + context.getYtdPension()
                    + context.getYtdEmploymentInsurance();
            YearEndAdjustmentResult adj = yearEndAdjustmentEngine.calculateYearEndAdjustment(
                    annualGross,
                    state.getEstimatedIncomeTax(),
                    state.getEstimatedInhabitantTax(),
                    state.getEstimatedSocialInsurance(),
                    context.getYtdGrossIncome(),
                    context.getYtdIncomeTax(),
                    context.getYtdResidentTax(),
                    ytdSocialInsurance);
            state.setEstimatedIncomeTax(adj.remainingIncomeTax());
            state.setEstimatedInhabitantTax(adj.remainingInhabitantTax());
            state.setEstimatedSocialInsurance(adj.remainingSocialInsurance());

        }
    }

    private void restoreNisaPrincipal(SimulationContext context) {
        if (context.getNisaPrincipalToRecoverNextYear() > 0) {
            context.setAccumulatedNisaPrincipal(
                    Math.max(0,
                            context.getAccumulatedNisaPrincipal() - context.getNisaPrincipalToRecoverNextYear()));
            context.setNisaPrincipalToRecoverNextYear(0L);
        }
        if (context.getGrowthNisaPrincipalToRecoverNextYear() > 0) {
            context.setAccumulatedGrowthNisaPrincipal(
                    Math.max(0, context.getAccumulatedGrowthNisaPrincipal()
                            - context.getGrowthNisaPrincipalToRecoverNextYear()));
            context.setGrowthNisaPrincipalToRecoverNextYear(0L);
        }
    }

    private AssetSimulationYearlyResult applyCashflowAndSettlement(SimulationContext context,
            long effectiveAnnualSalary, YearlySimulationState state,
            MortgageYearlyResult mortgageResult, InvestmentContributions contributions) {

        long fullYearNetIncome = state.getNetIncome();
        long ytdNetIncome = 0;

        if (context.getYear() == context.getCurrentYear() && (!context.isRetired() || effectiveAnnualSalary > 0)) {
            ytdNetIncome = computeYtdNetIncomeBaseline(context);
        }

        long remainingNetIncome = fullYearNetIncome - ytdNetIncome;
        long netSavings = remainingNetIncome - state.getAnnualExpenses();
        long remainingCashThisYear = netSavings - contributions.getTotalEmployeeInvestments()
                - mortgageResult.totalMortgagePaymentsThisYear();

        long currentTotalDcBalance = context.getDcBalance() + context.getIdecoBalance();
        context.setCurrentLiquidCash(context.getCurrentLiquidCash() + remainingCashThisYear);

        // --- NEGATIVE CASH LIQUIDATION LOGIC ---
        liquidationSimulationService.processNegativeCashflow(context);
        // --- END LIQUIDATION LOGIC ---

        long currentNetWorth = context.getCurrentLiquidCash() + context.getNisaBalance() + currentTotalDcBalance
                + context.getCompanyStockBalance();

        long remainingMortgageBalance = 0L;
        if (context.getMortgageBalances() != null) {
            for (long balance : context.getMortgageBalances()) {
                remainingMortgageBalance += balance;
            }
        }

        long trueNetWorth = currentNetWorth - remainingMortgageBalance;

        AssetSimulationYearlyResult dto = AssetSimulationYearlyResult.builder()
                .year(context.getYear())
                .age(context.getAge())
                .projectedAnnualSalary(effectiveAnnualSalary)
                .kouseiPensionContribution(state.getKouseiContribution())
                .accumulatedKouseiPension(context.getAccumulatedKousei())
                .expectedAnnualExpenses(state.getAnnualExpenses())
                .netSavings(netSavings)
                .projectedNetWorth(currentNetWorth)
                .projectedNisaBalance(context.getNisaBalance())
                .projectedDcBalance(currentTotalDcBalance)
                .projectedCompanyStockBalance(context.getCompanyStockBalance())
                .projectedLiquidCash(context.getCurrentLiquidCash())
                .isRetired(context.isRetired())
                .estimatedIncomeTax(state.getEstimatedIncomeTax())
                .estimatedInhabitantTax(state.getEstimatedInhabitantTax())
                .estimatedSocialInsurance(state.getEstimatedSocialInsurance())
                .netIncome(context.getYear() == context.getCurrentYear() ? remainingNetIncome : state.getNetIncome())
                .remainingCashThisYear(remainingCashThisYear)
                .remainingMortgageBalance(remainingMortgageBalance)
                .annualMortgagePayment(mortgageResult.totalMortgagePaymentsThisYear())
                .trueNetWorth(trueNetWorth)
                .build();
        return dto;
    }

    private long computeYtdNetIncomeBaseline(SimulationContext context) {
        return context.getYtdGrossIncome()
                - context.getYtdIncomeTax()
                - context.getYtdResidentTax()
                - context.getYtdHealthInsurance()
                - context.getYtdPension()
                - context.getYtdEmploymentInsurance();
    }
}
