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

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEventType;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;

class IncomeExpenseCalculatorTest {

    private IncomeExpenseCalculator calculator;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        calculator = new IncomeExpenseCalculator(null, null, null, null);
    }

    private SimulationContext createContext(int currentYear, int simYear) {
        SimulationContext context = new SimulationContext();
        context.setCurrentYear(currentYear);
        context.setYear(simYear);
        context.setBaselineMonthlyExpenses(0L);

        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setInflationRate(0.0);
        context.setConfig(config);

        return context;
    }

    @Test
    void testCalculateExpensesForYear_YearlyLifeEvent_ProratedAcrossYears() {
        // Event from 2030-09 to 2033-08 with annual cost of 1,200,000
        LifeEvent event = new LifeEvent(
                YearMonth.of(2030, 9),
                YearMonth.of(2033, 8),
                LifeEventFrequency.YEARLY,
                "Cram School Fees",
                1_200_000L);
        List<LifeEvent> events = List.of(event);

        // 2029: Not active yet
        SimulationContext context2029 = createContext(2026, 2029);
        long expenses2029 = calculator.calculateExpensesForYear(context2029, events, 12, 1);
        assertThat(expenses2029).isZero();

        // 2030 (Start year): Sep - Dec = 4 months -> 1,200,000 * 4 / 12 = 400,000
        SimulationContext context2030 = createContext(2026, 2030);
        long expenses2030 = calculator.calculateExpensesForYear(context2030, events, 12, 1);
        assertThat(expenses2030).isEqualTo(400_000L);

        // 2031 (Middle full year): Jan - Dec = 12 months -> 1,200,000
        SimulationContext context2031 = createContext(2026, 2031);
        long expenses2031 = calculator.calculateExpensesForYear(context2031, events, 12, 1);
        assertThat(expenses2031).isEqualTo(1_200_000L);

        // 2032 (Middle full year): Jan - Dec = 12 months -> 1,200,000
        SimulationContext context2032 = createContext(2026, 2032);
        long expenses2032 = calculator.calculateExpensesForYear(context2032, events, 12, 1);
        assertThat(expenses2032).isEqualTo(1_200_000L);

        // 2033 (End year): Jan - Aug = 8 months -> 1,200,000 * 8 / 12 = 800,000
        SimulationContext context2033 = createContext(2026, 2033);
        long expenses2033 = calculator.calculateExpensesForYear(context2033, events, 12, 1);
        assertThat(expenses2033).isEqualTo(800_000L);

        // 2034: Already ended
        SimulationContext context2034 = createContext(2026, 2034);
        long expenses2034 = calculator.calculateExpensesForYear(context2034, events, 12, 1);
        assertThat(expenses2034).isZero();
    }

    @Test
    void testCalculateExpensesForYear_YearlyLifeEvent_SameYear_Prorated() {
        // Event from 2030-03 to 2030-07 (5 months) with annual cost of 1,200,000
        LifeEvent event = new LifeEvent(
                YearMonth.of(2030, 3),
                YearMonth.of(2030, 7),
                LifeEventFrequency.YEARLY,
                "Short Term Project",
                1_200_000L);
        List<LifeEvent> events = List.of(event);

        SimulationContext context = createContext(2026, 2030);
        // 5 months -> Math.round((1,200,000 / 12.0) * 5) = 500,000
        long expenses = calculator.calculateExpensesForYear(context, events, 12, 1);
        assertThat(expenses).isEqualTo(500_000L);
    }

    @Test
    void testCalculateExpensesForYear_YearlyLifeEvent_TransitionYear_ProratedWithCurrentMonth() {
        // Current year is 2026, currentMonth is 9, monthsToAdd is 4 (Sep to Dec)
        SimulationContext context = createContext(2026, 2026);

        // Event starts earlier in 2026 (April 2026 to March 2028), cost 1,200,000
        LifeEvent event = new LifeEvent(
                YearMonth.of(2026, 4),
                YearMonth.of(2028, 3),
                LifeEventFrequency.YEARLY,
                "Ongoing Multi-year",
                1_200_000L);
        List<LifeEvent> events = List.of(event);

        // In 2026, months 4..8 already occurred; active remaining months are 9..12 (4
        // months)
        long expenses = calculator.calculateExpensesForYear(context, events, 4, 9);
        assertThat(expenses).isEqualTo(400_000L);
    }

    @Test
    void testCalculateExpensesForYear_YearlyLifeEvent_TransitionYear_AlreadyEnded_NoCost() {
        // Current year is 2026, currentMonth is 9, monthsToAdd is 4
        SimulationContext context = createContext(2026, 2026);

        // Event was Jan to June 2026 (already ended before current month 9)
        LifeEvent event = new LifeEvent(
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 6),
                LifeEventFrequency.YEARLY,
                "Past Event This Year",
                1_200_000L);
        List<LifeEvent> events = List.of(event);

        long expenses = calculator.calculateExpensesForYear(context, events, 4, 9);
        assertThat(expenses).isZero();
    }

    @Test
    void testCalculateExpensesForYear_MonthlyLifeEvent_ProratedAcrossYears() {
        // Event from 2030-09 to 2033-08 with monthly cost of 100,000
        LifeEvent event = new LifeEvent(
                YearMonth.of(2030, 9),
                YearMonth.of(2033, 8),
                LifeEventFrequency.MONTHLY,
                "Monthly Subscription",
                100_000L);
        List<LifeEvent> events = List.of(event);

        // 2030 (Start year): Sep - Dec = 4 months -> 100,000 * 4 = 400,000
        SimulationContext context2030 = createContext(2026, 2030);
        long expenses2030 = calculator.calculateExpensesForYear(context2030, events, 12, 1);
        assertThat(expenses2030).isEqualTo(400_000L);

        // 2031 (Middle full year): Jan - Dec = 12 months -> 100,000 * 12 = 1,200,000
        SimulationContext context2031 = createContext(2026, 2031);
        long expenses2031 = calculator.calculateExpensesForYear(context2031, events, 12, 1);
        assertThat(expenses2031).isEqualTo(1_200_000L);

        // 2033 (End year): Jan - Aug = 8 months -> 100,000 * 8 = 800,000
        SimulationContext context2033 = createContext(2026, 2033);
        long expenses2033 = calculator.calculateExpensesForYear(context2033, events, 12, 1);
        assertThat(expenses2033).isEqualTo(800_000L);
    }

    @Test
    void testCalculateSalaryForYearUntilMonth_aprilRaiseBeforeJuneRetirement_proratesCorrectly() {
        SimulationContext context = createContext(2026, 2026);
        context.setCurrentAnnualSalary(3_000_000L);
        context.setRetired(true);

        CareerEvent aprilRaise = new CareerEvent(
                YearMonth.of(2026, 4),
                "April Raise",
                CareerEventType.ABSOLUTE_OVERRIDE,
                5_000_000.0);

        // Jan-Mar (3 months) at 3,000,000 = 750,000; Apr-Jun (3 months) at 5,000,000 =
        // 1,250,000
        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(aprilRaise), null, 1, 6);
        assertThat(salary).isEqualTo(2_000_000L);
        assertThat(context.getCurrentAnnualSalary()).isEqualTo(5_000_000L);
    }

    @Test
    void testCalculateSalaryForYearUntilMonth_eventAfterEndMonth_ignoredInWindow() {
        SimulationContext context = createContext(2026, 2026);
        context.setCurrentAnnualSalary(3_000_000L);
        context.setRetired(true);

        CareerEvent augustEvent = new CareerEvent(
                YearMonth.of(2026, 8),
                "Post-Retirement Event",
                CareerEventType.ABSOLUTE_OVERRIDE,
                6_000_000.0);

        // Jan-Jun (6 months) at 3,000,000 = 1,500,000; August event must be ignored
        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(augustEvent), null, 1, 6);
        assertThat(salary).isEqualTo(1_500_000L);
        assertThat(context.getCurrentAnnualSalary()).isEqualTo(3_000_000L);
    }

    @Test
    void testCalculateSalaryForYearUntilMonth_initialYearWithYtdSalary_startsAfterYtdMonth() {
        SimulationContext context = createContext(2026, 2026);
        context.setCurrentAnnualSalary(3_000_000L);
        context.setRetired(true);

        CareerEvent aprilRaise = new CareerEvent(
                YearMonth.of(2026, 4),
                "April Raise",
                CareerEventType.ABSOLUTE_OVERRIDE,
                5_000_000.0);

        // YTD already covers Jan-Mar (1,000,000); window starts at April (startMonth=4,
        // endMonth=6)
        // Apr-Jun (3 months) at 5,000,000 = 1,250,000 -> total 2,250,000
        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(aprilRaise), 1_000_000L, 4, 6);
        assertThat(salary).isEqualTo(2_250_000L);
    }

    @Test
    void testCalculateSalaryForYearUntilMonth_emptyWindowWhenRetired_returnsZero() {
        SimulationContext context = createContext(2026, 2026);
        context.setCurrentAnnualSalary(3_000_000L);
        context.setRetired(true);

        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(), 1_500_000L, 7, 6);
        assertThat(salary).isZero();
        assertThat(context.getCurrentAnnualSalary()).isZero();
    }
}
