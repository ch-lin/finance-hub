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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.CashflowLiquidationCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.InvestmentReturnCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant.InhabitantTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InvestmentContributions;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.DependentDeductionEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.SpouseDeductionEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.SimulationParameterResolver;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

/**
 * Issue 194: Verifies Corporate DC retirement-year month proration, removal of
 * dcUnlockAge
 * contribution blocking, post-withdrawal stop guard (age >
 * taxAdvantagedWithdrawalAge),
 * and employer/employee matching deduction proration.
 */
@ExtendWith(MockitoExtension.class)
class YearlySimulationPipelineDcLifecycleTest {

    private static final long MONTHLY_EMPLOYER_DC = 30_000L;
    private static final long MONTHLY_EMPLOYEE_MATCHING = 20_000L;
    private static final long MONTHLY_TOTAL_DC = MONTHLY_EMPLOYER_DC + MONTHLY_EMPLOYEE_MATCHING; // 50,000 JPY

    @Mock
    private IncomeExpenseCalculator incomeExpenseCalculator;

    @Mock
    private MortgageCalculator mortgageSimulationService;

    private RecordingProjector projector;
    private YearlySimulationPipeline pipeline;

    private static class RecordingProjector extends TaxAdvantagedAccountProjector {
        final Map<Integer, InvestmentContributions> contributionsByAge = new HashMap<>();

        RecordingProjector(InvestmentReturnCalculator returnCalculator, ActiveTaxCalculationEngine taxEngine) {
            super(returnCalculator, taxEngine);
        }

        @Override
        public InvestmentContributions processAccountsForYear(
                SimulationContext context,
                int initialAge,
                int dcContributionMonths,
                int idecoContributionCeilingAge,
                int taxAdvantagedWithdrawalAge,
                int monthsToAdd,
                boolean isRetirementYear,
                int idecoContributionMonths) {
            InvestmentContributions result = super.processAccountsForYear(
                    context, initialAge, dcContributionMonths, idecoContributionCeilingAge,
                    taxAdvantagedWithdrawalAge, monthsToAdd, isRetirementYear, idecoContributionMonths);
            contributionsByAge.put(context.getAge(), result);
            return result;
        }
    }

    @BeforeEach
    void setUp() {
        ActiveTaxCalculationEngine taxEngine = new ActiveTaxCalculationEngine(
                new SpouseDeductionEngine(),
                new DependentDeductionEngine(),
                new BasicDeductionStrategyFactory(),
                new RetirementTaxStrategyFactory(),
                new EmploymentIncomeDeductionStrategyFactory(),
                new IncomeTaxRateStrategyFactory(),
                new InhabitantTaxRateStrategyFactory());

        projector = new RecordingProjector(new InvestmentReturnCalculator(), taxEngine);

        pipeline = new YearlySimulationPipeline(
                incomeExpenseCalculator,
                mortgageSimulationService,
                new CashflowLiquidationCalculator(),
                new PensionCalculator(),
                projector,
                taxEngine,
                new YearEndAdjustmentEngine());

        lenient().when(incomeExpenseCalculator.calculateExpensesForYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(0L);
        lenient().when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(6_000_000L);
        lenient().when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), anyInt()))
                .thenReturn(3_000_000L);
        lenient().when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt()))
                .thenReturn(3_000_000L);
        lenient()
                .when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(3_000_000L);
        lenient().when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any()))
                .thenReturn(new MortgageYearlyResult(0L, 0L, 0L, 0L, null));
    }

    private SimulationContext createContextWithDc(
            int currentYear,
            int initialAge,
            long initialDcBalance,
            LocalDate birthDate,
            RetirementMonthMode mode,
            Integer userDeclaredWithdrawalAge) {
        SimulationContext context = new SimulationContext();
        context.setCurrentYear(currentYear);
        context.setYear(currentYear);
        context.setCurrentAge(initialAge);
        context.setAge(initialAge);
        context.setCurrentAnnualSalary(6_000_000L);
        context.setCurrentLiquidCash(5_000_000L);
        context.setDcContributionYears(20);
        context.setDcMonthlyEmployerContribution(MONTHLY_EMPLOYER_DC);

        UserProfile profile = new UserProfile("test", birthDate, null, null);
        profile.setTaxAdvantagedWithdrawalAge(userDeclaredWithdrawalAge);
        context.setProfile(profile);

        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setRetirementMonthMode(mode);
        context.setConfig(config);

        context.setPensionConfig(new PensionConfigDto(
                816_000L, 480L, new BigDecimal("5.481"), 680_000L, 1_500_000L));
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        SimulationFund dcFund = new SimulationFund(
                1L, "Corporate DC Fund", initialDcBalance, MONTHLY_TOTAL_DC * 12L,
                0.0, null, "毎月", MONTHLY_TOTAL_DC);
        context.getDcFunds().add(dcFund);
        context.setDcBalance(initialDcBalance);
        return context;
    }

    /**
     * Standard retirement at 65 (June retirement -> 6 working months),
     * withdrawalAge = null (resolved to 65).
     * - Ages 60..64: full annual DC contribution (600,000) and matching (240,000);
     * not blocked by dcUnlockAge=60.
     * - Age 65 (retirement year): prorated 6 months total (300,000) and matching
     * (120,000); unlocks at 65.
     * - Age 66 (post-retirement): 0 total and 0 matching.
     */
    @Test
    void standardRetirementAt65_contributesThrough64_proratesTotalAndMatchingAt65_zeroAfter() {
        int initialAge = 59;
        int targetRetirementAge = 65;
        SimulationContext context = createContextWithDc(
                2026, initialAge, 1_000_000L,
                LocalDate.of(1967, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(65);

        for (int age = 59; age <= 64; age++) {
            pipeline.processSimulationYear(
                    context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, age);
            InvestmentContributions c = projector.contributionsByAge.get(age);
            assertThat(c.annualDcTotalContribution()).isEqualTo(600_000L);
            assertThat(c.annualDcMatchingContribution()).isEqualTo(240_000L);
        }
        // 6 years (59..64) * 600,000 + 1,000,000 = 4,600,000
        assertThat(context.getDcBalance()).isEqualTo(4_600_000L);

        // Age 65 (retirement year, June -> 6 months): total = 300,000, employer =
        // 180,000, matching = 120,000
        long cashBefore65 = context.getCurrentLiquidCash();
        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);
        InvestmentContributions c65 = projector.contributionsByAge.get(65);
        assertThat(c65.annualDcTotalContribution()).isEqualTo(300_000L);
        assertThat(c65.annualDcMatchingContribution()).isEqualTo(120_000L);
        assertThat(context.getDcBalance()).isEqualTo(0L);
        assertThat(context.getCurrentLiquidCash()).isGreaterThan(cashBefore65);

        // Age 66 (post-retirement): 0
        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 66);
        InvestmentContributions c66 = projector.contributionsByAge.get(66);
        assertThat(c66.annualDcTotalContribution()).isEqualTo(0L);
        assertThat(c66.annualDcMatchingContribution()).isEqualTo(0L);
    }

    /**
     * Initial year == retirement year intersection:
     * - initialAge = 65, currentMonth = April (4), retirementMonth = June (6).
     * - Effective DC months = 6 - 4 + 1 = 3 months: total = 150,000, matching =
     * 60,000.
     */
    @Test
    void initialYearIsRetirementYear_proratesRemainingWorkingMonthsForBothTotalAndMatching() {
        int initialAge = 65;
        int targetRetirementAge = 65;
        SimulationContext context = createContextWithDc(
                2026, initialAge, 1_000_000L,
                LocalDate.of(1961, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 4), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);

        InvestmentContributions c65 = projector.contributionsByAge.get(65);
        assertThat(c65.annualDcTotalContribution()).isEqualTo(150_000L);
        assertThat(c65.annualDcMatchingContribution()).isEqualTo(60_000L);
        assertThat(context.getDcBalance()).isEqualTo(0L);
    }

    /**
     * Missing birthDate under BIRTH_MONTH mode (retirementMonth == null) -> 0 DC
     * months & 0 matching.
     */
    @Test
    void retirementYearWithMissingBirthDate_zeroesTotalAndMatching() {
        int initialAge = 64;
        int targetRetirementAge = 65;
        SimulationContext context = createContextWithDc(
                2026, initialAge, 1_000_000L,
                null, RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);

        InvestmentContributions c65 = projector.contributionsByAge.get(65);
        assertThat(c65.annualDcTotalContribution()).isEqualTo(0L);
        assertThat(c65.annualDcMatchingContribution()).isEqualTo(0L);
    }

    /**
     * Withdrawal age earlier than retirement age (withdrawalAge = 60,
     * targetRetirementAge = 65):
     * - Age 60 (withdrawal year, still working): full 12 months DC contribution
     * (600,000 / 240,000 matching),
     * then unlocks at 60.
     * - Ages 61..64 (post-withdrawal, pre-retirement): 0 DC contribution and 0
     * matching (no empty deduction/cashflow).
     */
    @Test
    void withdrawalEarlierThanRetirement_fullYearAtWithdrawalAge_zeroAfterWithdrawal() {
        int initialAge = 59;
        int targetRetirementAge = 65;
        SimulationContext context = createContextWithDc(
                2026, initialAge, 1_000_000L,
                LocalDate.of(1967, 6, 15), RetirementMonthMode.BIRTH_MONTH, 60);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(60);

        // Age 60: withdrawal year, still employed full year -> full 12 months, then
        // unlocked
        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 60);
        InvestmentContributions c60 = projector.contributionsByAge.get(60);
        assertThat(c60.annualDcTotalContribution()).isEqualTo(600_000L);
        assertThat(c60.annualDcMatchingContribution()).isEqualTo(240_000L);
        assertThat(context.getDcBalance()).isEqualTo(0L);

        // Ages 61..64: post-withdrawal -> 0 contribution & 0 matching
        for (int age = 61; age <= 64; age++) {
            pipeline.processSimulationYear(
                    context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, age);
            InvestmentContributions c = projector.contributionsByAge.get(age);
            assertThat(c.annualDcTotalContribution()).isEqualTo(0L);
            assertThat(c.annualDcMatchingContribution()).isEqualTo(0L);
            assertThat(context.getDcBalance()).isEqualTo(0L);
        }
    }
}
