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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.MortgageTaxDeductionStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.TaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.utils.MortgageCalculatorUtil;
import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.InhabitantTaxLimitStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Post2022InhabitantTaxLimitStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Pre2022InhabitantTaxLimitStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.ZeroInhabitantTaxLimitStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;

@Component
public class MortgageCalculator {

    private final TaxStrategyFactory taxStrategyFactory;

    public MortgageCalculator(TaxStrategyFactory taxStrategyFactory) {
        this.taxStrategyFactory = taxStrategyFactory;
    }

    public void initializeMortgages(SimulationContext context, List<MortgageRecord> allMortgages) {
        context.setMortgageBalances(allMortgages.stream().mapToLong(m -> {
            if (m.getStartDate().isAfter(LocalDate.now())) {
                return 0L;
            }
            return m.getCurrentBalance();
        }).toArray());

        long[] initialPayments = new long[allMortgages.size()];
        double[] currentRates = new double[allMortgages.size()];
        long[] currentExpectedPayments = new long[allMortgages.size()];
        int[] monthsSinceStart = new int[allMortgages.size()];

        for (int i = 0; i < allMortgages.size(); i++) {
            MortgageRecord m = allMortgages.get(i);
            LocalDate recDate = m.getBalanceRecordDate() != null ? m.getBalanceRecordDate() : m.getStartDate();
            YearMonth recYm = YearMonth.from(recDate);
            int totalElapsed = (recYm.getYear() - m.getStartDate().getYear()) * 12
                    + (recYm.getMonthValue() - m.getStartDate().getMonthValue());

            currentRates[i] = m.getInterestRate();
            monthsSinceStart[i] = totalElapsed;

            Long monthlyPayment = m.getMonthlyPayment();
            long groundTruthPayment = monthlyPayment != null ? monthlyPayment : 0L;
            initialPayments[i] = groundTruthPayment;

            currentExpectedPayments[i] = initialPayments[i];
        }
        context.setMortgageMonthlyPayments(initialPayments);
        context.setMortgageCurrentRates(currentRates);
        context.setMortgageCurrentExpectedPayments(currentExpectedPayments);
        context.setMortgageMonthsSinceStart(monthsSinceStart);
    }

    private double getApplicableRate(MortgageRecord m, SimulationOverrides overrides, int currentYear) {
        if (m.getRateType() != InterestRateType.VARIABLE) {
            return m.getInterestRate();
        }
        if (overrides == null || overrides.mortgageRateScenarios() == null) {
            return m.getInterestRate();
        }
        List<SimulationOverrides.MortgageRateScenarioOverride> list = overrides.mortgageRateScenarios().get(m.getId());
        if (list == null || list.isEmpty()) {
            return m.getInterestRate();
        }
        double applicableRate = m.getInterestRate();
        int maxStartYear = -1;
        for (var scenario : list) {
            if (scenario.startYear() <= currentYear && scenario.startYear() > maxStartYear) {
                maxStartYear = scenario.startYear();
                applicableRate = scenario.rate();
            }
        }
        return applicableRate;
    }

    private List<SimulationOverrides.MortgageEarlyRepaymentOverride> getOverrideEarlyRepayments(
            MortgageRecord m, YearMonth currentYm, SimulationOverrides overrides, SimulationContext context) {
        if (overrides == null || overrides.mortgageEarlyRepayments() == null) {
            return List.of();
        }
        List<SimulationOverrides.MortgageEarlyRepaymentOverride> list = overrides.mortgageEarlyRepayments()
                .get(m.getId());
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        Integer configuredMonth = (context != null && context.getConfig() != null)
                ? context.getConfig().getSimulationEarlyRepaymentMonth()
                : null;
        int logicalMonth = (configuredMonth != null) ? configuredMonth : 12;

        if (currentYm.getMonthValue() == logicalMonth) {
            return list.stream()
                    .filter(override -> override.year().equals(currentYm.getYear()))
                    .toList();
        }
        return List.of();
    }

    public MortgageYearlyResult simulateMortgagesForYear(SimulationContext context, List<MortgageRecord> allMortgages,
            YearMonth nowMonth, SimulationOverrides overrides) {
        long[] mortgageBalances = context.getMortgageBalances();
        long[] mortgageMonthlyPayments = context.getMortgageMonthlyPayments();
        double[] currentRates = context.getMortgageCurrentRates();
        long[] expectedPayments = context.getMortgageCurrentExpectedPayments();
        int[] monthsSinceStart = context.getMortgageMonthsSinceStart();
        int year = context.getYear();

        long totalMortgagePaymentsThisYear = 0L;
        long yearEndDeduction = 0L;
        long kakuteiDeduction = 0L;
        long maxTotalIncomeForMortgageDeduction = 0L;
        InhabitantTaxLimitStrategy bestStrategy = new ZeroInhabitantTaxLimitStrategy();

        for (int i = 0; i < allMortgages.size(); i++) {
            MortgageRecord m = allMortgages.get(i);
            long balance = mortgageBalances[i];
            boolean isFutureStartThisYear = m.getStartDate().getYear() == year && balance == 0;
            if (balance <= 0 && !isFutureStartThisYear) {
                continue;
            }

            long annualPayment = 0L;

            for (int month = 1; month <= 12; month++) {
                YearMonth currentYm = YearMonth.of(year, month);
                boolean hasStarted = !currentYm.isBefore(YearMonth.from(m.getStartDate()));
                if (!hasStarted) {
                    continue;
                }

                if (currentYm.equals(YearMonth.from(m.getStartDate())) && balance == 0) {
                    balance = m.getCurrentBalance();
                }

                if (balance <= 0) {
                    break;
                }
                boolean isFutureMonth = !currentYm.isBefore(nowMonth);

                if (isFutureMonth) {
                    monthsSinceStart[i]++;
                    double newRate = getApplicableRate(m, overrides, currentYm.getYear());
                    boolean rateChangedThisMonth = false;
                    if (newRate != currentRates[i]) {
                        currentRates[i] = newRate;
                        rateChangedThisMonth = true;
                    }

                    if (m.getRepaymentMethod() == ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod.EQUAL_PAYMENT) {
                        boolean hasFiveYearRule = Boolean.TRUE.equals(m.getHasFiveYearRule());
                        if (hasFiveYearRule) {
                            if (monthsSinceStart[i] % 60 == 0) {
                                int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                        + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
                                        - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                remainingMonths = Math.max(1, remainingMonths);
                                if (remainingMonths > 0) {
                                    long theoreticalNewPayment = MortgageCalculatorUtil
                                            .calculateFixedPaymentOrPrincipal(
                                                    balance, currentRates[i], remainingMonths, m.getRepaymentMethod(),
                                                    m.getBonusPayments(),
                                                    currentYm.getMonthValue());
                                    long maxAllowedPayment = (long) (expectedPayments[i] * 1.25);
                                    long newPayment = Math.min(theoreticalNewPayment, maxAllowedPayment);
                                    expectedPayments[i] = newPayment;
                                    mortgageMonthlyPayments[i] = newPayment;
                                }
                            }
                        } else {
                            if (rateChangedThisMonth) {
                                int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                        + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
                                        - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                remainingMonths = Math.max(1, remainingMonths);
                                if (remainingMonths > 0) {
                                    long newPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
                                            balance, currentRates[i], remainingMonths, m.getRepaymentMethod(),
                                            m.getBonusPayments(),
                                            currentYm.getMonthValue());
                                    expectedPayments[i] = newPayment;
                                    mortgageMonthlyPayments[i] = newPayment;
                                }
                            }
                        }
                    }
                }

                double monthlyRate = currentRates[i] / 12.0;

                long earlyPrincipal = 0L;
                long earlyFee = 0L;

                if (isFutureMonth) {
                    for (var override : getOverrideEarlyRepayments(m, currentYm, overrides, context)) {
                        earlyPrincipal += override.amount();
                        if (override.repaymentType() == EarlyRepaymentType.REDUCE_PAYMENT) {
                            int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                    + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                            int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
                                    - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                            remainingMonths = Math.max(1, remainingMonths);
                            if (remainingMonths > 0) {
                                mortgageMonthlyPayments[i] = MortgageCalculatorUtil
                                        .calculateFixedPaymentOrPrincipal(
                                                Math.max(0L, balance - earlyPrincipal), currentRates[i], remainingMonths,
                                                m.getRepaymentMethod(),
                                                m.getBonusPayments(),
                                                currentYm.getMonthValue());
                                if (m.getRepaymentMethod() == RepaymentMethod.EQUAL_PAYMENT) {
                                    expectedPayments[i] = mortgageMonthlyPayments[i];
                                }
                            }
                        }
                    }
                }

                if (isFutureMonth && m.getEarlyRepayments() != null) {
                    for (var er : m.getEarlyRepayments()) {
                        if (YearMonth.from(er.getRepaymentDate()).equals(currentYm)) {
                            earlyPrincipal += er.getAmount();
                            earlyFee += er.getFee();
                            if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_PAYMENT) {
                                int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                                        + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
                                int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
                                        - MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, currentYm);
                                remainingMonths = Math.max(1, remainingMonths);
                                if (remainingMonths > 0) {
                                    mortgageMonthlyPayments[i] = MortgageCalculatorUtil
                                            .calculateFixedPaymentOrPrincipal(
                                                    balance - earlyPrincipal, currentRates[i], remainingMonths,
                                                    m.getRepaymentMethod(),
                                                    m.getBonusPayments(),
                                                    currentYm.getMonthValue());
                                    if (m.getRepaymentMethod() == RepaymentMethod.EQUAL_PAYMENT) {
                                        expectedPayments[i] = mortgageMonthlyPayments[i];
                                    }
                                }
                            }
                        }
                    }
                }

                if (isFutureMonth) {
                    if (earlyPrincipal > balance) {
                        earlyPrincipal = balance;
                    }
                    if (earlyPrincipal > 0) {
                        context.setExecutedEarlyRepaymentPrincipal(earlyPrincipal);
                    }
                    balance -= earlyPrincipal;
                    annualPayment += earlyPrincipal + earlyFee;
                }
                if (balance <= 0) {
                    balance = 0;
                    break;
                }

                long payment = mortgageMonthlyPayments[i];
                long currentMonthBonus = 0;
                if (m.getBonusPayments() != null) {
                    for (var bp : m.getBonusPayments()) {
                        if (bp.getMonth() != null && bp.getMonth() == month) {
                            Long amount = bp.getAmount();
                            currentMonthBonus += amount != null ? amount : 0L;
                        }
                    }
                }
                payment += currentMonthBonus;

                long interest = (long) (balance * monthlyRate);
                long principal;

                if (m.getRepaymentMethod() == ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod.EQUAL_PRINCIPAL) {
                    principal = mortgageMonthlyPayments[i];
                    principal += currentMonthBonus;
                    payment = principal + interest;
                } else {
                    principal = payment - interest;
                }

                if (isFutureMonth) {
                    if (principal > balance) {
                        principal = balance;
                        if (m.getRepaymentMethod() == ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod.EQUAL_PRINCIPAL) {
                            payment = balance + interest;
                        } else {
                            payment = balance + interest;
                        }
                    }
                    // For EQUAL_PAYMENT, if principal is negative (unpaid interest), it will be
                    // added to balance
                    balance -= principal;
                    annualPayment += payment;
                }
            }

            mortgageBalances[i] = balance;
            totalMortgagePaymentsThisYear += annualPayment;

            if (Boolean.TRUE.equals(m.getTaxDeductionEligible())) {
                LocalDate occupancyDate = m.getOccupancyDate();
                Integer endYear = m.getTaxDeductionEndYear();
                if (occupancyDate != null && endYear != null
                        && year >= occupancyDate.getYear() && year <= endYear) {
                    double rate = m.getTaxDeductionRate();
                    long deduction = (long) (balance * rate);

                    long maxDeduction = m.getMaxTaxDeductionAmount();

                    if (deduction > maxDeduction) {
                        deduction = maxDeduction;
                    }

                    boolean isOccupancyYear = (year == occupancyDate.getYear());
                    boolean noYearEndAdjustmentAvailable = isOccupancyYear || context.isRetired();

                    if (noYearEndAdjustmentAvailable) {
                        kakuteiDeduction = deduction;
                        yearEndDeduction = 0L;
                    } else {
                        yearEndDeduction = deduction;
                        kakuteiDeduction = 0L;
                    }

                    MortgageTaxDeductionStrategy deductionStrategy = taxStrategyFactory.getStrategy(occupancyDate);
                    if (deductionStrategy != null) {
                        maxTotalIncomeForMortgageDeduction = deductionStrategy.getMaxEligibleTotalIncome();
                    }

                    InhabitantTaxLimitStrategy currentStrategy = new Pre2022InhabitantTaxLimitStrategy();
                    if (occupancyDate.getYear() >= 2022) {
                        currentStrategy = new Post2022InhabitantTaxLimitStrategy();
                    }
                    if (currentStrategy.getFixedLimit() > bestStrategy.getFixedLimit()) {
                        bestStrategy = currentStrategy;
                    }
                }
            }
        }

        return new MortgageYearlyResult(
                totalMortgagePaymentsThisYear,
                yearEndDeduction,
                kakuteiDeduction,
                maxTotalIncomeForMortgageDeduction,
                bestStrategy);
    }
}
