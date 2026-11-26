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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Pre2022TaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.SpecialSpecificAcquisitionTaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.TaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2022To2023TaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2024To2025TaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2026OnwardsTaxStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;

class MortgageCalculatorTest {

    private MortgageCalculator calculator;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        TaxStrategyFactory taxStrategyFactory = new TaxStrategyFactory(List.of(
                new Pre2022TaxStrategy(),
                new SpecialSpecificAcquisitionTaxStrategy(),
                new Years2022To2023TaxStrategy(),
                new Years2024To2025TaxStrategy(),
                new Years2026OnwardsTaxStrategy()));
        calculator = new MortgageCalculator(taxStrategyFactory);
    }

    @Test
    void testSimulateMortgagesForYear_FirstEligibleYear_DefersTaxDeduction() {
        // Arrange
        SimulationContext context = new SimulationContext();
        context.setYear(2026);

        // Current balance 10,000,000, 0.7% rate -> deduction 70,000
        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "Test Loan",
                10000000L,
                10000000L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2056, 1, 1),
                LocalDate.of(2026, 1, 1),
                360,
                1.0,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                30000L,
                Collections.emptyList(),
                LocalDate.of(2026, 2, 1), // Occupancy in 2026
                null,
                null,
                true,
                2039,
                0.007,
                273000L,
                true);

        long[] balances = new long[] { 10000000L };
        long[] payments = new long[] { 30000L };
        double[] rates = new double[] { 1.0 };
        long[] expected = new long[] { 30000L };
        int[] months = new int[] { 0 };

        context.setMortgageBalances(balances);
        context.setMortgageMonthlyPayments(payments);
        context.setMortgageCurrentRates(rates);
        context.setMortgageCurrentExpectedPayments(expected);
        context.setMortgageMonthsSinceStart(months);

        YearMonth nowMonth = YearMonth.of(2026, 1);
        SimulationOverrides overrides = null;

        // Act
        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);

        // Assert
        // In the first year (2026 == occupancy year), the deduction goes to
        // kakuteiDeduction channel
        assertEquals(0L, result.yearEndDeduction());
        assertTrue(result.kakuteiDeduction() > 0);
        assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
    }

    @Test
    void testSimulateMortgagesForYear_SecondEligibleYear_AppliesTaxDeduction() {
        // Arrange
        SimulationContext context = new SimulationContext();
        context.setYear(2027);

        // Occupancy in 2026, so 2027 is the second year
        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "Test Loan",
                10000000L,
                10000000L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2056, 1, 1),
                LocalDate.of(2026, 1, 1),
                360,
                1.0,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                30000L,
                Collections.emptyList(),
                LocalDate.of(2026, 2, 1), // Occupancy in 2026
                null,
                null,
                true,
                2039,
                0.007,
                273000L,
                true);

        long[] balances = new long[] { 10000000L };
        long[] payments = new long[] { 30000L };
        double[] rates = new double[] { 1.0 };
        long[] expected = new long[] { 30000L };
        int[] months = new int[] { 12 }; // 12 months passed

        context.setMortgageBalances(balances);
        context.setMortgageMonthlyPayments(payments);
        context.setMortgageCurrentRates(rates);
        context.setMortgageCurrentExpectedPayments(expected);
        context.setMortgageMonthsSinceStart(months);

        YearMonth nowMonth = YearMonth.of(2027, 1);
        SimulationOverrides overrides = null;

        // Act
        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);

        // Assert
        // In the second year (2027 > 2026), the deduction for THIS year should be in
        // yearEndDeduction channel
        assertTrue(result.yearEndDeduction() > 0);
        assertEquals(0L, result.kakuteiDeduction());
        assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
    }

    @Test
    void testSimulateMortgagesForYear_PriorToOccupancyYear_NoTaxDeduction() {
        // Arrange: Loan executed in Nov 2027, but occupancy in Feb 2028
        SimulationContext context2027 = new SimulationContext();
        context2027.setYear(2027);

        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "Future Handover Loan",
                10000000L,
                10000000L, // Starting balance upon disbursement
                LocalDate.of(2027, 11, 1),
                LocalDate.of(2057, 11, 1),
                LocalDate.of(2027, 11, 1),
                360,
                0.01,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                30000L,
                Collections.emptyList(),
                LocalDate.of(2028, 2, 1), // Occupancy in 2028!
                null,
                null,
                true,
                2040,
                0.007,
                273000L,
                true);

        context2027.setMortgageBalances(new long[] { 0L });
        context2027.setMortgageMonthlyPayments(new long[] { 30000L });
        context2027.setMortgageCurrentRates(new double[] { 0.01 });
        context2027.setMortgageCurrentExpectedPayments(new long[] { 30000L });
        context2027.setMortgageMonthsSinceStart(new int[] { 0 });

        YearMonth nowMonth = YearMonth.of(2027, 1);

        // Act 1: Simulate Year 2027 (loan starts in Nov 2027, but not yet occupied in
        // 2027)
        MortgageYearlyResult result2027 = calculator.simulateMortgagesForYear(context2027, List.of(m), nowMonth, null);

        // Assert 1: In 2027 (prior to occupancy year 2028), tax deductions MUST
        // strictly be 0
        assertEquals(0L, result2027.yearEndDeduction(), "No year-end tax deduction before occupancy year");
        assertEquals(0L, result2027.kakuteiDeduction(), "No kakutei tax deduction before occupancy year");

        // Act 2: Simulate Year 2028 (occupancy year)
        SimulationContext context2028 = new SimulationContext();
        context2028.setYear(2028);
        context2028.setMortgageBalances(context2027.getMortgageBalances());
        context2028.setMortgageMonthlyPayments(context2027.getMortgageMonthlyPayments());
        context2028.setMortgageCurrentRates(context2027.getMortgageCurrentRates());
        context2028.setMortgageCurrentExpectedPayments(context2027.getMortgageCurrentExpectedPayments());
        context2028.setMortgageMonthsSinceStart(context2027.getMortgageMonthsSinceStart());

        MortgageYearlyResult result2028 = calculator.simulateMortgagesForYear(context2028, List.of(m), nowMonth, null);

        // Assert 2: In 2028 (occupancy year), kakuteiDeduction is awarded,
        // yearEndDeduction is 0
        assertTrue(result2028.kakuteiDeduction() > 0, "Kakutei deduction applies in occupancy year");
        assertEquals(0L, result2028.yearEndDeduction(), "No year-end deduction in first occupancy year");
    }

    @Test
    void testSimulateMortgagesForYear_ReducePaymentOverride_RecalculatesAndReducesMonthlyPayment() {
        // Arrange
        SimulationContext context = new SimulationContext();
        context.setYear(2026);

        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "Loan 1",
                10000000L,
                10000000L,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2055, 1, 1),
                LocalDate.of(2026, 1, 1),
                360,
                0.012, // 1.2%
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                33000L,
                Collections.emptyList(),
                LocalDate.of(2025, 2, 1),
                null,
                null,
                false,
                null,
                null,
                null,
                true);
        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);

        long initialPayment = 33000L;
        context.setMortgageBalances(new long[] { 10000000L });
        context.setMortgageMonthlyPayments(new long[] { initialPayment });
        context.setMortgageCurrentRates(new double[] { 0.012 });
        context.setMortgageCurrentExpectedPayments(new long[] { initialPayment });
        context.setMortgageMonthsSinceStart(new int[] { 12 });

        // Override in month 12 of 2026 with REDUCE_PAYMENT for 3,000,000
        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride = new SimulationOverrides.MortgageEarlyRepaymentOverride(
                2026, 3000000L, EarlyRepaymentType.REDUCE_PAYMENT);
        SimulationOverrides overrides = new SimulationOverrides(
                null, null, null, null, null,
                Map.of(1L, List.of(erOverride)),
                null, null, null);

        YearMonth nowMonth = YearMonth.of(2026, 1);

        // Act
        calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);

        // Assert:
        // Monthly payment should be recalculated down from 33,000 (around
        // 22,000~24,000)
        long newPayment = context.getMortgageMonthlyPayments()[0];
        assertTrue(newPayment < 26000L, "Monthly payment should be reduced significantly, was: " + newPayment);
        assertTrue(newPayment > 15000L, "Monthly payment should be reasonable, was: " + newPayment);
        assertEquals(newPayment, context.getMortgageCurrentExpectedPayments()[0]);
        // Balance should be reduced by amortization + 3m early repayment
        assertTrue(context.getMortgageBalances()[0] < 7000000L);
    }

    @Test
    void testSimulateMortgagesForYear_ReduceTermOverride_LeavesMonthlyPaymentUnchanged() {
        // Arrange
        SimulationContext context = new SimulationContext();
        context.setYear(2026);

        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "Loan 1",
                10000000L,
                10000000L,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2055, 1, 1),
                LocalDate.of(2026, 1, 1),
                360,
                0.012, // 1.2%
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                33000L,
                Collections.emptyList(),
                LocalDate.of(2025, 2, 1),
                null,
                null,
                false,
                null,
                null,
                null,
                true);
        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);

        long initialPayment = 33000L;
        context.setMortgageBalances(new long[] { 10000000L });
        context.setMortgageMonthlyPayments(new long[] { initialPayment });
        context.setMortgageCurrentRates(new double[] { 0.012 });
        context.setMortgageCurrentExpectedPayments(new long[] { initialPayment });
        context.setMortgageMonthsSinceStart(new int[] { 12 });

        // Override in month 12 of 2026 with REDUCE_TERM for 3,000,000
        SimulationOverrides.MortgageEarlyRepaymentOverride erOverride = new SimulationOverrides.MortgageEarlyRepaymentOverride(
                2026, 3000000L, EarlyRepaymentType.REDUCE_TERM);
        SimulationOverrides overrides = new SimulationOverrides(
                null, null, null, null, null,
                Map.of(1L, List.of(erOverride)),
                null, null, null);

        YearMonth nowMonth = YearMonth.of(2026, 1);

        // Act
        calculator.simulateMortgagesForYear(context, List.of(m), nowMonth, overrides);

        // Assert:
        // Monthly payment should remain 33,000
        assertEquals(initialPayment, context.getMortgageMonthlyPayments()[0]);
        assertEquals(initialPayment, context.getMortgageCurrentExpectedPayments()[0]);
        // Balance should still be reduced by amortization + 3m early repayment
        assertTrue(context.getMortgageBalances()[0] < 7000000L);
    }

    @Test
    void testSimulateMortgagesForYear_Pre2022Occupancy_Returns30MIncomeLimit() {
        SimulationContext context = new SimulationContext();
        context.setYear(2021);
        context.setMortgageBalances(new long[] { 10_000_000L });
        context.setMortgageMonthlyPayments(new long[] { 30_000L });
        context.setMortgageCurrentRates(new double[] { 1.0 });
        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
        context.setMortgageMonthsSinceStart(new int[] { 12 });

        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "Old Loan",
                10000000L,
                10000000L,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2050, 1, 1),
                LocalDate.of(2021, 1, 1),
                360,
                1.0,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                30000L,
                Collections.emptyList(),
                LocalDate.of(2020, 5, 1), // Occupancy in 2020 (Pre-2022)
                null,
                null,
                true,
                2030,
                0.01,
                400000L,
                true);

        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2021, 1),
                null);

        assertEquals(30_000_000L, result.maxTotalIncomeForMortgageDeduction());
    }

    @Test
    void testSimulateMortgagesForYear_Post2022Occupancy_Returns20MIncomeLimit() {
        SimulationContext context = new SimulationContext();
        context.setYear(2026);
        context.setMortgageBalances(new long[] { 10_000_000L });
        context.setMortgageMonthlyPayments(new long[] { 30_000L });
        context.setMortgageCurrentRates(new double[] { 1.0 });
        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
        context.setMortgageMonthsSinceStart(new int[] { 12 });

        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "New Loan",
                10000000L,
                10000000L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2056, 1, 1),
                LocalDate.of(2026, 1, 1),
                360,
                1.0,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                30000L,
                Collections.emptyList(),
                LocalDate.of(2026, 2, 1), // Occupancy in 2026 (Post-2022)
                null,
                null,
                true,
                2039,
                0.007,
                273000L,
                true);

        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2026, 1),
                null);

        assertEquals(20_000_000L, result.maxTotalIncomeForMortgageDeduction());
    }

    @Test
    void testSimulateMortgagesForYear_NotEligible_ReturnsZeroIncomeLimit() {
        SimulationContext context = new SimulationContext();
        context.setYear(2026);
        context.setMortgageBalances(new long[] { 10_000_000L });
        context.setMortgageMonthlyPayments(new long[] { 30_000L });
        context.setMortgageCurrentRates(new double[] { 1.0 });
        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
        context.setMortgageMonthsSinceStart(new int[] { 12 });

        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "Ineligible Loan",
                10000000L,
                10000000L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2056, 1, 1),
                LocalDate.of(2026, 1, 1),
                360,
                1.0,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                30000L,
                Collections.emptyList(),
                LocalDate.of(2026, 2, 1),
                null,
                null,
                false, // Not eligible
                null,
                null,
                null,
                true);

        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2026, 1),
                null);

        assertEquals(0L, result.maxTotalIncomeForMortgageDeduction());
    }

    @Test
    void testSimulateMortgagesForYear_SecondEligibleYear_Retired_RoutesToKakuteiDeduction() {
        SimulationContext context = new SimulationContext();
        context.setYear(2027); // Year 2 after 2026 occupancy
        context.setRetired(true); // Retired
        context.setMortgageBalances(new long[] { 10_000_000L });
        context.setMortgageMonthlyPayments(new long[] { 30_000L });
        context.setMortgageCurrentRates(new double[] { 1.0 });
        context.setMortgageCurrentExpectedPayments(new long[] { 30_000L });
        context.setMortgageMonthsSinceStart(new int[] { 24 });

        MortgageRecord m = new MortgageRecord(
                "ソニー銀行",
                "1",
                "New Loan",
                10000000L,
                10000000L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2056, 1, 1),
                LocalDate.of(2026, 1, 1),
                360,
                1.0,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                30000L,
                Collections.emptyList(),
                LocalDate.of(2026, 2, 1), // Occupancy in 2026
                null,
                null,
                true,
                2039,
                0.007,
                273000L,
                true);

        MortgageYearlyResult result = calculator.simulateMortgagesForYear(context, List.of(m), YearMonth.of(2027, 1),
                null);

        // When retired, Year 2+ mortgage deduction must route to kakuteiDeduction (0
        // for yearEndDeduction)
        assertTrue(result.kakuteiDeduction() > 0L);
        assertEquals(0L, result.yearEndDeduction());
    }
}
