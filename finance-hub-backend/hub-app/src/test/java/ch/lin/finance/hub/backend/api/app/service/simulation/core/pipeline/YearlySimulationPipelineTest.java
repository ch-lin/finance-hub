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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.CashflowLiquidationCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.TaxAdvantagedAccountProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Post2022InhabitantTaxLimitStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant.InhabitantTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InvestmentContributions;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.ActiveTaxCalculationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.DependentDeductionEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.SpouseDeductionEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.YearEndAdjustmentEngine;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

@ExtendWith(MockitoExtension.class)
class YearlySimulationPipelineTest {

    @Mock
    private IncomeExpenseCalculator incomeExpenseCalculator;

    @Mock
    private MortgageCalculator mortgageSimulationService;

    @Mock
    private TaxAdvantagedAccountProjector taxAdvantagedAccountProjector;

    private YearlySimulationPipeline pipeline;

    @BeforeEach
    void setUp() {
        SpouseDeductionEngine spouseEngine = new SpouseDeductionEngine();
        DependentDeductionEngine dependentEngine = new DependentDeductionEngine();
        BasicDeductionStrategyFactory basicDeductionStrategyFactory = new BasicDeductionStrategyFactory();
        RetirementTaxStrategyFactory retirementTaxStrategyFactory = new RetirementTaxStrategyFactory();
        EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory = new EmploymentIncomeDeductionStrategyFactory();
        IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory = new IncomeTaxRateStrategyFactory();
        InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory = new InhabitantTaxRateStrategyFactory();
        ActiveTaxCalculationEngine taxEngine = new ActiveTaxCalculationEngine(
                spouseEngine, dependentEngine, basicDeductionStrategyFactory, retirementTaxStrategyFactory,
                employmentIncomeDeductionStrategyFactory, incomeTaxRateStrategyFactory,
                inhabitantTaxRateStrategyFactory);

        PensionCalculator pensionCalculator = new PensionCalculator();
        CashflowLiquidationCalculator liquidationSimulationService = new CashflowLiquidationCalculator();
        YearEndAdjustmentEngine yearEndAdjustmentEngine = new YearEndAdjustmentEngine();

        pipeline = new YearlySimulationPipeline(
                incomeExpenseCalculator,
                mortgageSimulationService,
                liquidationSimulationService,
                pensionCalculator,
                taxAdvantagedAccountProjector,
                taxEngine,
                yearEndAdjustmentEngine);

        lenient()
                .when(taxAdvantagedAccountProjector.processAccountsForYear(any(), anyInt(), anyInt(), anyInt(),
                        anyInt(), anyInt(), anyBoolean(), anyInt()))
                .thenReturn(new InvestmentContributions(0L, 0L, 0L, 0L));
        lenient().when(incomeExpenseCalculator.calculateExpensesForYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(0L);
    }

    private SimulationContext createContext(int year, int age, long salary, long initialCash) {
        SimulationContext context = new SimulationContext();
        context.setCurrentYear(year);
        context.setYear(year);
        context.setCurrentAge(age);
        context.setAge(age);
        context.setCurrentAnnualSalary(salary);
        context.setCurrentLiquidCash(initialCash);
        context.setProfile(new UserProfile("test", null, null, null));
        context.setPensionConfig(new PensionConfigDto(
                816_000L,
                480L,
                new BigDecimal("5.481"),
                680_000L,
                1_500_000L));
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);
        return context;
    }

    /**
     * Scenario A: Standard full deduction across multiple years.
     * - Year 1 (Occupancy): 210,000 JPY deduction routed via Kakutei Shinkoku;
     * actual paid income tax > 250,000 JPY.
     * - Year 2: Spring cash strictly disburses 210,000 JPY refund; Year 2 Year-End
     * Adjustment applies 210,000 JPY deduction.
     * - Year 3: Spring cash refund is 0 JPY (prevents duplicate refund).
     */
    @Test
    void testScenarioA_standardFullDeduction_multiYearCycle() {
        int initialAge = 35;
        long salary = 8_000_000L;
        SimulationContext context = createContext(2026, initialAge, salary, 1_000_000L);
        YearMonth nowMonth = YearMonth.of(2026, 1);

        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(salary);
        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);

        // --- Year 1 (2026): Occupancy year ---
        // Mortgage deduction is 210,000 JPY, routed via kakuteiDeduction channel
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 210_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 35);

        assertThat(resultYear1.getYear()).isEqualTo(2026);
        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
        assertThat(recordAfterYear1).isNotNull();
        // With 8M salary, baseIncomeTax is > 250,000 JPY, so 210,000 JPY is fully
        // refundable
        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(210_000L);
        assertThat(recordAfterYear1.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);

        long cashAtEndOfYear1 = context.getCurrentLiquidCash();

        // --- Year 2 (2027): Second year ---
        // Mortgage deduction is 210,000 JPY, routed via yearEndDeduction channel
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 210_000L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);

        assertThat(resultYear2.getYear()).isEqualTo(2027);

        // Year-end adjustment directly reduced Year 2 estimated income tax by 210,000
        // JPY (plus 2.1% reconstruction tax)
        long expectedIncomeTaxReduction = 210_000L + (long) (210_000L * 0.021);
        assertThat(resultYear1.getEstimatedIncomeTax() - resultYear2.getEstimatedIncomeTax())
                .isEqualTo(expectedIncomeTaxReduction);

        // Spring cash received the 210,000 JPY refund from Year 1
        // Net savings of Year 2 is higher by expectedIncomeTaxReduction compared to
        // Year 1, plus 210,000 JPY spring cash refund
        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
        long expectedCashDelta = resultYear2.getNetSavings() + 210_000L;
        assertThat(cashDelta).isEqualTo(expectedCashDelta);

        // Record for Year 3: pendingKakuteiShinkokuRefund must be 0
        PreviousYearTaxRecord recordAfterYear2 = context.getPreviousYearTaxRecord();
        assertThat(recordAfterYear2).isNotNull();
        assertThat(recordAfterYear2.pendingKakuteiShinkokuRefund()).isEqualTo(0L);

        long cashAtEndOfYear2 = context.getCurrentLiquidCash();

        // --- Year 3 (2028): Third year ---
        AssetSimulationYearlyResult resultYear3 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 37);

        assertThat(resultYear3.getYear()).isEqualTo(2028);

        // Verify NO duplicate spring cash refund in Year 3:
        // Cash change from Year 2 to Year 3 is purely netSavings (0 refund added)
        long cashDeltaYear3 = resultYear3.getProjectedLiquidCash() - cashAtEndOfYear2;
        assertThat(cashDeltaYear3).isEqualTo(resultYear3.getNetSavings());
    }

    /**
     * Scenario B: Low-income with inhabitant tax rollover (Core Issue 137 fix
     * verification).
     * - Year 1: Mortgage deduction is 210,000 JPY, but baseIncomeTax is capped
     * (~103,200 JPY < 210,000 JPY).
     * - Year 2: Spring cash increases by the capped paid tax (NOT 210,000 JPY,
     * solves Issue 137 Problem 2).
     * - Year 2: Inhabitant tax deducts exactly 97,500 JPY (statutory rollover cap,
     * solves Issue 137 Problem 1).
     */
    @Test
    void testScenarioB_lowIncomeWithInhabitantRollover_cappedRefundAndCredit() {
        int initialAge = 35;
        long salary = 4_500_000L;
        SimulationContext context = createContext(2026, initialAge, salary, 1_000_000L);
        YearMonth nowMonth = YearMonth.of(2026, 1);

        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(salary);
        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);

        // --- Year 1 (2026): First year Kakutei Shinkoku ---
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 210_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 35);

        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
        assertThat(recordAfterYear1).isNotNull();

        // Income tax is capped below 210,000 JPY
        long cappedRefund = recordAfterYear1.pendingKakuteiShinkokuRefund();
        assertThat(cappedRefund).isGreaterThan(0L);
        assertThat(cappedRefund).isLessThan(210_000L);

        // Leftover mortgage deduction rollover is capped at statutory inhabitant tax
        // cap (<= 97,500 JPY)
        long rollover = recordAfterYear1.unusedMortgageDeductionForInhabitantTax();
        assertThat(rollover).isGreaterThan(0L);
        assertThat(rollover).isLessThanOrEqualTo(97_500L);

        long cashAtEndOfYear1 = context.getCurrentLiquidCash();

        // --- Year 2 (2027): Mortgage deduction ends (0L) to isolate rollover effect
        // ---
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);

        // Problem 1 Verification: Year 2 inhabitant tax is credited by exactly the
        // rollover amount
        assertThat(resultYear1.getEstimatedInhabitantTax() - resultYear2.getEstimatedInhabitantTax())
                .isEqualTo(rollover);

        // Problem 2 Verification: Year 2 spring cash refund received EXACTLY the capped
        // amount, NOT 210,000 JPY
        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
        long expectedCashDelta = resultYear2.getNetSavings() + cappedRefund;
        assertThat(cashDelta).isEqualTo(expectedCashDelta);
    }

    /**
     * Scenario B (Direct): Verifies the exact statutory illustration:
     * With 50,000 JPY paid income tax and 97,500 JPY inhabitant tax rollover from
     * previous year,
     * Year 2 spring cash increases by exactly 50,000 JPY, and inhabitant tax
     * reduces by exactly 97,500 JPY.
     */
    @Test
    void testScenarioB_exactStatutoryIllustration_refundCappedAt50kAndRollover97500() {
        int initialAge = 35;
        long salary = 4_500_000L;
        long initialCash = 1_000_000L;
        SimulationContext context = createContext(2027, 36, salary, initialCash);
        YearMonth nowMonth = YearMonth.of(2027, 1);

        // Pre-package previous year tax record with 50,000 JPY refund and 97,500 JPY
        // inhabitant rollover
        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                50_000L, 97_500L, 0L));

        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(salary);
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);

        // Inhabitant tax credited by 97,500 JPY (206,600 uncredited base + 5,000 flat
        // levy - 97,500 = 114,100 JPY)
        assertThat(result.getEstimatedInhabitantTax()).isEqualTo(114_100L);

        // Spring cash refunded exactly 50,000 JPY
        long cashDelta = result.getProjectedLiquidCash() - initialCash;
        long expectedCashDelta = result.getNetSavings() + 50_000L;
        assertThat(cashDelta).isEqualTo(expectedCashDelta);
    }

    /**
     * Scenario C: High income disqualification phase-out (Issue 139 verification).
     * - Year 1: Gross salary is 25,000,000 JPY (employment income > 20,000,000 JPY
     * statutory threshold).
     * - Year 2: Spring cash refund is 0 JPY; Inhabitant tax credit is 0 JPY.
     */
    @Test
    void testScenarioC_highIncomeDisqualification_phaseOut() {
        int initialAge = 35;
        long highSalary = 25_000_000L;
        SimulationContext context = createContext(2026, initialAge, highSalary, 1_000_000L);
        YearMonth nowMonth = YearMonth.of(2026, 1);

        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(highSalary);
        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(highSalary);

        // High earner enters mortgage in year 1
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 210_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 35);

        assertThat(resultYear1.getYear()).isEqualTo(2026);

        // Phase-out check in Year 1: both refund and inhabitant rollover must be
        // strictly 0
        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
        assertThat(recordAfterYear1).isNotNull();
        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
        assertThat(recordAfterYear1.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);

        long cashAtEndOfYear1 = context.getCurrentLiquidCash();

        // --- Year 2 (2027) ---
        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, 65, 65, initialAge, 36);

        // Zero spring cash refund received in Year 2 (cash change is strictly net
        // savings)
        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
        assertThat(cashDelta).isEqualTo(resultYear2.getNetSavings());
    }

    /**
     * Issue 172 Verification:
     * When user retires in the initial simulation year (age ==
     * targetRetirementAge),
     * YTD net income from historical salary earned earlier this year must NOT be
     * subtracted
     * from fullYearNetIncome (which is 0 or pension), preventing phantom deduction
     * of liquid cash.
     */
    @Test
    void testInitialYearRetirement_doesNotSubtractYtdNetIncomeFromLiquidCash() {
        int initialAge = 65;
        int targetRetirementAge = 65;
        int pensionStartAge = 70;
        long initialCash = 5_000_000L;
        SimulationContext context = createContext(2026, initialAge, 0L, initialCash);
        YearMonth nowMonth = YearMonth.of(2026, 7);

        // Populate YTD salary and deductions already received and saved in bank cash
        // (net = 2,312,000 JPY)
        context.setYtdGrossIncome(3_000_000L);
        context.setYtdIncomeTax(100_000L);
        context.setYtdResidentTax(150_000L);
        context.setYtdHealthInsurance(150_000L);
        context.setYtdPension(270_000L);
        context.setYtdEmploymentInsurance(18_000L);

        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(0L);
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, initialAge);

        // Before fix: remainingNetIncome was 0 - 2,312,000 = -2,312,000 JPY.
        // With Direction A fix: ytdNetIncome is not subtracted when retired, so
        // netIncome is 0L.
        assertThat(result.isRetired()).isTrue();
        assertThat(result.getNetIncome()).isEqualTo(0L);

        // Health insurance for early retiree (age < 75 with 0 pension) is added to
        // annualExpenses:
        // 400,000 / 12 * 6 = 200,000 JPY
        long expectedAnnualExpenses = Math.round((TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM / 12.0) * 6);
        assertThat(result.getExpectedAnnualExpenses()).isEqualTo(expectedAnnualExpenses);

        // Cash delta is only -expectedAnnualExpenses; 2,312,000 JPY YTD net income was
        // NOT deducted
        long expectedCash = initialCash - expectedAnnualExpenses;
        assertThat(result.getProjectedLiquidCash()).isEqualTo(expectedCash);
    }

    /**
     * Question 191-03 Test 1:
     * Future retirement year split-year (age > initialAge):
     * Working segment (Jan-Jun) and retiree segment (Jul-Dec) are each calculated
     * and summed into state.
     * No YTD adjustment packaged into PreviousYearTaxRecord.
     */
    @Test
    void testFutureRetirementYear_splitYear_sumsWorkingAndPensionTaxes() {
        int initialAge = 60;
        int targetRetirementAge = 65;
        int pensionStartAge = 65;
        int retirementAge = 65;
        long salary = 6_000_000L;
        long initialCash = 5_000_000L;
        SimulationContext context = createContext(2026, initialAge, salary, initialCash);
        context.setCurrentKokuminMonths(480);
        context.setCurrentKouseiMonths(480);
        context.setAccumulatedKousei(1_000_000L);
        YearMonth nowMonth = YearMonth.of(2026, 1);

        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
        context.setConfig(config);
        context.getProfile().setBirthDate(LocalDate.of(1966, 6, 15)); // Month 6

        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), eq(1), eq(6)))
                .thenReturn(3_000_000L);
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, retirementAge);

        assertThat(result.getYear()).isEqualTo(2031);
        assertThat(result.getAge()).isEqualTo(65);
        assertThat(result.isRetired()).isTrue();
        assertThat(result.getProjectedAnnualSalary()).isEqualTo(3_000_000L);
        assertThat(result.getEstimatedIncomeTax()).isGreaterThan(0L);
        assertThat(result.getNetIncome()).isGreaterThan(0L);

        // Future retirement year: no YTD difference packaging
        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
        if (record != null) {
            assertThat(record.pendingWorkingSegmentTaxAdjustment()).isEqualTo(0L);
        }
    }

    /**
     * Question 191-03 Test 2:
     * Initial year retirement split-year (age == initialAge):
     * Calculates working segment tax, compares with YTD history, and packages the
     * difference
     * into PreviousYearTaxRecord.pendingWorkingSegmentTaxAdjustment without calling
     * applyYearEndAdjustment.
     */
    @Test
    void testInitialYearRetirement_splitYear_packagesWorkingSegmentYtdAdjustment() {
        int initialAge = 65;
        int targetRetirementAge = 65;
        int pensionStartAge = 65;
        long salary = 6_000_000L;
        long initialCash = 5_000_000L;
        SimulationContext context = createContext(2026, initialAge, salary, initialCash);
        context.setCurrentKokuminMonths(480);
        context.setCurrentKouseiMonths(480);
        context.setAccumulatedKousei(1_000_000L);
        YearMonth nowMonth = YearMonth.of(2026, 3); // Month 3

        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
        context.setConfig(config);
        context.getProfile().setBirthDate(LocalDate.of(1961, 6, 15)); // Month 6

        // Working segment salary for remaining months (e.g. 3,000,000)
        context.setYtdBaseSalary(1_500_000L);
        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(3_000_000L);
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));

        // YTD deductions recorded up to March (gross: 1,500,000)
        context.setYtdGrossIncome(1_500_000L);
        context.setYtdIncomeTax(100_000L);
        context.setYtdResidentTax(80_000L);
        context.setYtdHealthInsurance(75_000L);
        context.setYtdPension(135_000L);
        context.setYtdEmploymentInsurance(9_000L);

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                nowMonth, 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, initialAge);

        assertThat(result.getYear()).isEqualTo(2026);
        assertThat(result.isRetired()).isTrue();
        assertThat(result.getProjectedAnnualSalary()).isEqualTo(3_000_000L);

        // Verify YTD difference packaged into PreviousYearTaxRecord
        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
        assertThat(record).isNotNull();
        assertThat(record.pendingWorkingSegmentTaxAdjustment()).isNotEqualTo(0L);
    }

    /**
     * Question 191-03 Test 3:
     * Next-year simulation:
     * Pending working segment tax adjustment is disbursed into liquid cash (+ for
     * refund)
     * and cleared from PreviousYearTaxRecord to prevent double redemption.
     */
    @Test
    void testNextYearSimulation_disbursesWorkingSegmentTaxAdjustmentAndClearsRecord() {
        int initialAge = 65;
        int targetRetirementAge = 65;
        int pensionStartAge = 65;
        long initialCash = 5_000_000L;
        long pendingAdj = 120_000L; // Refund due to excess YTD withholding
        SimulationContext context = createContext(2027, 66, 0L, initialCash);

        // Pre-package PreviousYearTaxRecord with pendingWorkingSegmentTaxAdjustment
        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                0L, 0L, pendingAdj));

        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(0L);
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2027, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 66);

        // 1. Adjustment disbursed into liquid cash
        long cashDelta = result.getProjectedLiquidCash() - initialCash;
        assertThat(cashDelta).isEqualTo(result.getNetSavings() + pendingAdj);

        // 2. Pending adjustment zeroed out to prevent duplicate payout in following
        // year
        PreviousYearTaxRecord updatedRecord = context.getPreviousYearTaxRecord();
        assertThat(updatedRecord).isNotNull();
        assertThat(updatedRecord.pendingWorkingSegmentTaxAdjustment()).isEqualTo(0L);
    }

    /**
     * Question 191-03 Test 4:
     * Retirement year with DECEMBER mode:
     * Works through December 31 (12 months in-job, 0 months pension).
     */
    @Test
    void testRetirementYear_decemberMode_fullYearWorkingNoPension() {
        int initialAge = 60;
        int targetRetirementAge = 65;
        int pensionStartAge = 65;
        long salary = 6_000_000L;
        long initialCash = 5_000_000L;
        SimulationContext context = createContext(2026, initialAge, salary, initialCash);

        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setRetirementMonthMode(RetirementMonthMode.DECEMBER);
        context.setConfig(config);
        context.getProfile().setBirthDate(LocalDate.of(1966, 4, 10)); // Birthday in April, but DECEMBER mode

        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), eq(1), eq(12)))
                .thenReturn(salary);
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 65);

        assertThat(result.getYear()).isEqualTo(2031);
        assertThat(result.getProjectedAnnualSalary()).isEqualTo(salary);
        // Working taxes applied
        assertThat(result.getEstimatedIncomeTax()).isGreaterThan(0L);

        // No YTD difference packaged
        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
        if (record != null) {
            assertThat(record.pendingWorkingSegmentTaxAdjustment()).isEqualTo(0L);
        }
    }

    /**
     * Question 191-03 Test 5:
     * Missing birthDate under BIRTH_MONTH mode:
     * Fallback to full-year retired (0 working months, 12 pension months) without
     * NPE.
     */
    @Test
    void testRetirementYear_missingBirthDateUnderBirthMonthMode_fallbackToFullYearRetired() {
        int initialAge = 60;
        int targetRetirementAge = 65;
        int pensionStartAge = 65;
        long initialCash = 5_000_000L;
        SimulationContext context = createContext(2026, initialAge, 6_000_000L, initialCash);
        context.setCurrentKokuminMonths(480);
        context.setCurrentKouseiMonths(480);
        context.setAccumulatedKousei(1_000_000L);

        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
        context.setConfig(config);
        context.getProfile().setBirthDate(null); // Missing birthDate

        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 65);

        assertThat(result.getYear()).isEqualTo(2031);
        assertThat(result.getProjectedAnnualSalary()).isEqualTo(0L);
        assertThat(result.isRetired()).isTrue();
        // Pension calculated for 12 months
        assertThat(result.getNetIncome()).isGreaterThan(0L);
    }

    /**
     * Question 191-04 Test 1:
     * Post-retirement full retiree year with mortgage deduction:
     * Year 1 (Age 66):
     * - Retired (12 months pension).
     * - Mortgage deduction routed to kakuteiDeduction (100,000 JPY).
     * - calculatePensionTax caps mortgage refund against pension baseIncomeTax.
     * - Pending refund and unused deduction packaged into PreviousYearTaxRecord.
     * Year 2 (Age 67):
     * - Spring cash refund strictly disburses pendingKakuteiShinkokuRefund into
     * liquid cash.
     * - pendingKakuteiShinkokuRefund is zeroed out in PreviousYearTaxRecord to
     * prevent duplicate payout.
     */
    @Test
    void testQuestion191_04_postRetirementYear_mortgageDeductionPackagesKakuteiRefund_andDisbursesNextYear() {
        int initialAge = 66;
        int targetRetirementAge = 65;
        int pensionStartAge = 65;
        long initialCash = 5_000_000L;
        SimulationContext context = createContext(2026, initialAge, 0L, initialCash);
        context.setCurrentKokuminMonths(480);
        context.setCurrentKouseiMonths(480);
        context.setAccumulatedKousei(3_000_000L);

        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), any())).thenReturn(0L);
        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any())).thenReturn(0L);

        // --- Year 1 (2026, Age 66): Post-retirement year with 100,000 JPY mortgage
        // deduction ---
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 100_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult resultYear1 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 66);

        assertThat(resultYear1.getYear()).isEqualTo(2026);
        assertThat(resultYear1.isRetired()).isTrue();
        assertThat(resultYear1.getProjectedAnnualSalary()).isEqualTo(0L);

        PreviousYearTaxRecord recordAfterYear1 = context.getPreviousYearTaxRecord();
        assertThat(recordAfterYear1).isNotNull();
        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isGreaterThan(0L);
        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isLessThanOrEqualTo(100_000L);
        long expectedRefund = recordAfterYear1.pendingKakuteiShinkokuRefund();
        assertThat(recordAfterYear1.pendingKakuteiShinkokuRefund()).isEqualTo(expectedRefund);

        long cashAtEndOfYear1 = context.getCurrentLiquidCash();

        // --- Year 2 (2027, Age 67): Next year spring cash payout ---
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult resultYear2 = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2027, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 67);

        assertThat(resultYear2.getYear()).isEqualTo(2027);

        // Spring cash received expectedRefund
        long cashDelta = resultYear2.getProjectedLiquidCash() - cashAtEndOfYear1;
        assertThat(cashDelta).isEqualTo(resultYear2.getNetSavings() + expectedRefund);

        // Record has refund zeroed to prevent double redemption
        PreviousYearTaxRecord recordAfterYear2 = context.getPreviousYearTaxRecord();
        assertThat(recordAfterYear2).isNotNull();
        assertThat(recordAfterYear2.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
    }

    /**
     * Question 191-04 Test 2:
     * Split retirement year:
     * Mortgage deduction relays across working segment and pension segment.
     */
    @Test
    void testQuestion191_04_splitRetirementYear_mortgageDeductionRelaysAcrossWorkingAndPensionSegments() {
        int initialAge = 60;
        int targetRetirementAge = 65;
        int pensionStartAge = 65;
        SimulationContext context = createContext(2026, initialAge, 6_000_000L, 5_000_000L);
        context.setCurrentKokuminMonths(480);
        context.setCurrentKouseiMonths(480);
        context.setAccumulatedKousei(3_000_000L);

        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setRetirementMonthMode(RetirementMonthMode.BIRTH_MONTH);
        context.setConfig(config);
        context.getProfile().setBirthDate(LocalDate.of(1966, 6, 15)); // Month = 6 (split year: 6 working, 6 pension)

        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), eq(1), eq(6)))
                .thenReturn(3_000_000L);
        when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                new MortgageYearlyResult(0L, 0L, 250_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));

        AssetSimulationYearlyResult result = pipeline.processSimulationYear(
                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                YearMonth.of(2026, 1), 60, 60, 60, targetRetirementAge, pensionStartAge, initialAge, 65);

        assertThat(result.getYear()).isEqualTo(2031);
        assertThat(result.isRetired()).isTrue();

        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
        assertThat(record).isNotNull();
        // Combined refund must be capped within kakuteiDeduction
        assertThat(record.pendingKakuteiShinkokuRefund()).isGreaterThan(0L);
        assertThat(record.pendingKakuteiShinkokuRefund()).isLessThanOrEqualTo(250_000L);
    }
}
