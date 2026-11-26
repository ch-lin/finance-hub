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
 * Issue 193: Verifies iDeCo contribution ceiling age, retirement-year month
 * proration,
 * and unified DC/iDeCo unlock age resolution (preserving Plan-09 coupling).
 */
@ExtendWith(MockitoExtension.class)
class YearlySimulationPipelineIdecoLifecycleTest {

    private static final long MONTHLY_IDECO = 20_000L; // 240,000 JPY / year

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
        lenient().when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(3_000_000L);
        lenient().when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any()))
                .thenReturn(new MortgageYearlyResult(0L, 0L, 0L, 0L, null));
    }

    private SimulationContext createContextWithIdeco(
            int currentYear,
            int initialAge,
            long initialIdecoBalance,
            long initialDcBalance,
            double returnRate,
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
        context.setIdecoContributionYears(20);
        context.setDcContributionYears(20);

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

        SimulationFund idecoFund = new SimulationFund(
                1L, "iDeCo Fund", initialIdecoBalance, MONTHLY_IDECO * 12L,
                returnRate, null, "毎月", MONTHLY_IDECO);
        context.getIdecoFunds().add(idecoFund);
        context.setIdecoBalance(initialIdecoBalance);

        if (initialDcBalance > 0) {
            SimulationFund dcFund = new SimulationFund(
                    2L, "DC Fund", initialDcBalance, 0L, returnRate, null, "毎月", 0L);
            context.getDcFunds().add(dcFund);
            context.setDcBalance(initialDcBalance);
        }
        return context;
    }

    /**
     * Scenario 1: Retire at 65, userDeclaredWithdrawalAge = null, June birthday.
     * - Ages 60..64: annualIdecoContribution = 240,000 each year and balance
     * accumulates.
     * - Age 65: ceiling & retirement year (June) -> prorated 6 months (120,000),
     * and DC & iDeCo unlock together at 65.
     */
    @Test
    void scenario1_retireAt65_nullWithdrawalAge_contributesThrough64_proratesAt65AndUnlocks() {
        int initialAge = 59;
        int targetRetirementAge = 65;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 2_000_000L, 0.0,
                LocalDate.of(1967, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(65);

        for (int age = 59; age <= 64; age++) {
            pipeline.processSimulationYear(
                    context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, age);
            assertThat(projector.contributionsByAge.get(age).annualIdecoContribution())
                    .as("Age %d should contribute full annual iDeCo", age)
                    .isEqualTo(240_000L);
            assertThat(context.getIdecoBalance()).isPositive();
            assertThat(context.getDcBalance()).isPositive();
        }
        // 6 years (59..64) * 240,000 + 1,000,000 = 2,440,000
        assertThat(context.getIdecoBalance()).isEqualTo(2_440_000L);

        // Age 65: prorated to June (6 months = 120,000) and unlocks both DC and iDeCo
        // together
        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);
        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
        assertThat(context.getDcBalance()).isEqualTo(0L);
    }

    /**
     * Scenario 2: Retire at 65, userDeclaredWithdrawalAge = 68, June birthday.
     * - Age 65 (ceiling & retirement year): prorated 6 months (120,000), balance
     * compounds.
     * - Ages 66..67: 0 contribution, balance continues compounding.
     * - Age 68: 0 contribution, unlocks at 68.
     */
    @Test
    void scenario2_retireAt65_withdrawalAt68_proratesAt65_compoundsThrough67_unlocksAt68() {
        int initialAge = 64;
        int targetRetirementAge = 65;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.10,
                LocalDate.of(1962, 6, 15), RetirementMonthMode.BIRTH_MONTH, 68);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(68);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 64);
        assertThat(projector.contributionsByAge.get(64).annualIdecoContribution()).isEqualTo(240_000L);
        long expected = context.getIdecoBalance();

        // Age 65: prorated 6 months (120,000)
        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);
        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
        expected = Math.round(expected * 1.10 + 120_000L * 1.05);
        assertThat(context.getIdecoBalance()).isEqualTo(expected);

        // Ages 66..67: 0 contribution, pure compounding
        for (int age = 66; age <= 67; age++) {
            pipeline.processSimulationYear(
                    context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, age);
            assertThat(projector.contributionsByAge.get(age).annualIdecoContribution()).isEqualTo(0L);
            expected = Math.round(expected * 1.10);
            assertThat(context.getIdecoBalance()).isEqualTo(expected);
        }

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 68);
        assertThat(projector.contributionsByAge.get(68).annualIdecoContribution()).isEqualTo(0L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
    }

    /**
     * Scenario 3: Retire at 62, userDeclaredWithdrawalAge = 62, June birthday.
     * - Ages 60, 61: normal contribution.
     * - Age 62: ceiling & retirement year (June) -> prorated 6 months (120,000) and
     * unlocks at 62.
     */
    @Test
    void scenario3_retireAt62_withdrawalAt62_contributesThrough61_proratesAndUnlocksAt62() {
        int initialAge = 60;
        int targetRetirementAge = 62;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.0,
                LocalDate.of(1966, 6, 15), RetirementMonthMode.BIRTH_MONTH, 62);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(62);

        for (int age = 60; age <= 61; age++) {
            pipeline.processSimulationYear(
                    context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, age);
            assertThat(projector.contributionsByAge.get(age).annualIdecoContribution()).isEqualTo(240_000L);
        }

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 62);
        assertThat(projector.contributionsByAge.get(62).annualIdecoContribution()).isEqualTo(120_000L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
    }

    /**
     * Retirement-year proration when retiring before 65 without earlier
     * user-declared ceiling:
     * - Non-initial retirement year (age 62, June): prorates 6 months (120,000) and
     * unlocks at 62.
     */
    @Test
    void earlyRetirementAt62_nullWithdrawalAge_proratesRetirementMonthsAndUnlocksAt62() {
        int initialAge = 60;
        int targetRetirementAge = 62;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.0,
                LocalDate.of(1966, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(62);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 60);
        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 61);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 62);
        assertThat(projector.contributionsByAge.get(62).annualIdecoContribution()).isEqualTo(120_000L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
    }

    /**
     * Initial transition year == retirement year intersection:
     * - initialAge = 62, currentMonth = April (4), retirementMonth = June (6).
     * - Effective iDeCo contribution = 6 - 4 + 1 = 3 months (60,000 JPY).
     */
    @Test
    void initialYearIsRetirementYear_proratesOnlyRemainingWorkingMonths() {
        int initialAge = 62;
        int targetRetirementAge = 62;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.0,
                LocalDate.of(1964, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 4), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 62);
        assertThat(projector.contributionsByAge.get(62).annualIdecoContribution()).isEqualTo(60_000L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
    }

    /**
     * Scenario 4: FIRE at 45 (June retirement), userDeclaredWithdrawalAge = null.
     * - Age 45: prorated 6 months contribution (120,000), remains locked.
     * - Ages 46..59: 0 contribution, pure compounding.
     * - Age 60: unlocks at statutory floor 60.
     */
    @Test
    void scenario4_fireAt45_proratesAt45_zeroAfterRetirement_unlocksAt60() {
        int initialAge = 44;
        int targetRetirementAge = 45;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.05,
                LocalDate.of(1982, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(60);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 44);
        long balanceAfter44 = context.getIdecoBalance();

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 45);
        assertThat(projector.contributionsByAge.get(45).annualIdecoContribution()).isEqualTo(120_000L);
        long expected = Math.round(balanceAfter44 * 1.05 + 120_000L * 1.025);
        assertThat(context.getIdecoBalance()).isEqualTo(expected);

        for (int age = 46; age <= 59; age++) {
            pipeline.processSimulationYear(
                    context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, age);
            assertThat(projector.contributionsByAge.get(age).annualIdecoContribution()).isEqualTo(0L);
            expected = Math.round(expected * 1.05);
            assertThat(context.getIdecoBalance()).isEqualTo(expected);
        }

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 60);
        assertThat(projector.contributionsByAge.get(60).annualIdecoContribution()).isEqualTo(0L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
    }

    /**
     * Scenario 5: Delayed retirement at 68, userDeclaredWithdrawalAge = null, June
     * birthday.
     * - Age 65: ceiling year (not retirement year) -> prorated to birth month (6
     * months = 120,000), compounds.
     * - Ages 66..67: 0 contribution, balance continues compounding.
     * - Age 68: unlocks at 68.
     */
    @Test
    void scenario5_delayedRetirementAt68_nullWithdrawalAge_proratesAt65_compounds65To67_unlocksAt68() {
        int initialAge = 64;
        int targetRetirementAge = 68;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.10,
                LocalDate.of(1962, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(68);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 64);
        assertThat(projector.contributionsByAge.get(64).annualIdecoContribution()).isEqualTo(240_000L);
        long expected = context.getIdecoBalance();

        // Age 65: ceiling year -> prorated 6 months (120,000)
        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 65);
        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
        expected = Math.round(expected * 1.10 + 120_000L * 1.05);
        assertThat(context.getIdecoBalance()).isEqualTo(expected);

        // Ages 66..67: 0 contribution, pure compounding
        for (int age = 66; age <= 67; age++) {
            long prevBalance = context.getIdecoBalance();
            pipeline.processSimulationYear(
                    context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, age);
            assertThat(projector.contributionsByAge.get(age).annualIdecoContribution()).isEqualTo(0L);
            expected = Math.round(expected * 1.10);
            assertThat(context.getIdecoBalance()).isEqualTo(expected).isGreaterThan(prevBalance);
        }

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 68);
        assertThat(projector.contributionsByAge.get(68).annualIdecoContribution()).isEqualTo(0L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
    }

    /**
     * Scenario 6: Retire at 60, userDeclaredWithdrawalAge = 60 (earliest unlock
     * age), June birthday.
     * - Age 59: contributes normally.
     * - Age 60: ceiling & retirement year (June) -> prorated 6 months (120,000) and
     * unlocks at 60.
     */
    @Test
    void scenario6_retireAt60_withdrawalAt60_proratesAndUnlocksAt60() {
        int initialAge = 59;
        int targetRetirementAge = 60;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.0,
                LocalDate.of(1967, 6, 15), RetirementMonthMode.BIRTH_MONTH, 60);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);
        assertThat(withdrawalAge).isEqualTo(60);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 59);
        assertThat(projector.contributionsByAge.get(59).annualIdecoContribution()).isEqualTo(240_000L);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 60);
        assertThat(projector.contributionsByAge.get(60).annualIdecoContribution()).isEqualTo(120_000L);
        assertThat(context.getIdecoBalance()).isEqualTo(0L);
    }

    /**
     * Issue 196: Retire at 65 under DECEMBER mode with June birthday -> capped at
     * birthMonth (6 months = 120,000).
     */
    @Test
    void decemberRetirementModeAt65_capsIdecoAtBirthMonth() {
        int initialAge = 65;
        int targetRetirementAge = 65;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.0,
                LocalDate.of(1961, 6, 15), RetirementMonthMode.DECEMBER, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);
        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
    }

    /**
     * Issue 196: Initial year == ceiling year (age 65, April start, June birthday)
     * -> 6 - 4 + 1 = 3 months (60,000).
     */
    @Test
    void initialYearIsCeilingYear_proratesFromCurrentMonthToBirthMonth() {
        int initialAge = 65;
        int targetRetirementAge = 68;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.0,
                LocalDate.of(1961, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 4), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 65);
        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(60_000L);
    }

    /**
     * Issue 196: Ceiling year with missing birthDate -> conservatively zeroes
     * contribution.
     */
    @Test
    void ceilingYearWithMissingBirthDate_zeroesContribution() {
        int initialAge = 65;
        int targetRetirementAge = 68;
        SimulationContext context = createContextWithIdeco(
                2026, initialAge, 1_000_000L, 0L, 0.0,
                null, RetirementMonthMode.BIRTH_MONTH, null);
        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
                context.getProfile(), 60, 60, null, targetRetirementAge);

        pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 65);
        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(0L);
    }
}
