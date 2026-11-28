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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation.TransactionLinkRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation.TransactionLink;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IncomeExpenseCalculator {

    private final BankTransactionRepository bankTransactionRepository;
    private final CreditCardTransactionRepository creditCardTransactionRepository;
    private final TransactionLinkRepository transactionLinkRepository;

    public long calculateSalaryForYear(SimulationContext context, List<CareerEvent> careerEvents) {
        return calculateSalaryForYear(context, careerEvents, null, null);
    }

    public long calculateSalaryForYear(SimulationContext context, List<CareerEvent> careerEvents, Long ytdSalary,
            Integer monthsToAdd) {
        if (context.isRetired() && (monthsToAdd == null || monthsToAdd == 0)) {
            context.setCurrentAnnualSalary(0L);
            return 0L;
        }
        int startMonth = (monthsToAdd != null) ? (12 - monthsToAdd + 1) : 1;
        return calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, 12);
    }

    /**
     * Calculates salary for an explicit [startMonth, endMonth] window (1-12),
     * used for the pre-retirement working segment in a split retirement year.
     */
    public long calculateSalaryForYearUntilMonth(SimulationContext context, List<CareerEvent> careerEvents,
            Long ytdSalary, int startMonth, int endMonth) {
        if (context.isRetired() && endMonth < startMonth) {
            context.setCurrentAnnualSalary(0L);
            return 0L;
        }
        return calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, endMonth);
    }

    private long calculateSalaryForWindow(SimulationContext context, List<CareerEvent> careerEvents,
            Long ytdSalary, int startMonth, int endMonth) {
        long currentSalary = context.getCurrentAnnualSalary();
        if (context.getYear() > context.getCurrentYear()) {
            double salaryGrowthRate = context.getSalaryGrowthRate();
            if (salaryGrowthRate > 0) {
                currentSalary = Math.round(currentSalary * (1.0 + salaryGrowthRate));
            }
        }

        long totalRemainingAnnualized = 0;
        int lastMonth = startMonth;
        long salaryAtLastMonth = currentSalary;

        List<CareerEvent> sortedEvents = careerEvents.stream()
                .sorted(Comparator.comparing(e -> e.getEventYearMonth()))
                .toList();

        for (CareerEvent event : sortedEvents) {
            if (event.getEventYearMonth().getYear() == context.getYear()) {
                int eventMonth = event.getEventYearMonth().getMonthValue();

                if (eventMonth < startMonth) {
                    switch (event.getEventType()) {
                        case ABSOLUTE_OVERRIDE ->
                            salaryAtLastMonth = event.getEventValue().longValue();
                        case RELATIVE_CHANGE ->
                            salaryAtLastMonth = (long) (salaryAtLastMonth * event.getEventValue());
                    }
                    continue;
                }
                if (eventMonth > endMonth) {
                    continue;
                }

                int monthsAtOldSalary = eventMonth - lastMonth;
                if (monthsAtOldSalary > 0) {
                    totalRemainingAnnualized += salaryAtLastMonth * monthsAtOldSalary;
                }

                long newSalary = salaryAtLastMonth;
                switch (event.getEventType()) {
                    case ABSOLUTE_OVERRIDE ->
                        newSalary = event.getEventValue().longValue();
                    case RELATIVE_CHANGE ->
                        newSalary = (long) (salaryAtLastMonth * event.getEventValue());
                }

                salaryAtLastMonth = newSalary;
                lastMonth = eventMonth;
            }
        }

        int remainingMonths = endMonth - lastMonth + 1;
        if (remainingMonths > 0) {
            totalRemainingAnnualized += salaryAtLastMonth * remainingMonths;
        }

        context.setCurrentAnnualSalary(salaryAtLastMonth);

        long calculatedRemaining = Math.round(totalRemainingAnnualized / 12.0);
        return ytdSalary != null ? (ytdSalary + calculatedRemaining) : calculatedRemaining;
    }

    public long calculateExpensesForYear(SimulationContext context, List<LifeEvent> lifeEvents, int monthsToAdd,
            int currentMonth) {
        long annualExpenses = context.getBaselineMonthlyExpenses() * monthsToAdd;
        int year = context.getYear();

        Double configInflation = context.getConfig() != null ? context.getConfig().getInflationRate() : null;
        double inflationRate = configInflation != null ? configInflation : 0.0;
        if (inflationRate > 0) {
            int yearsFromNow = year - context.getCurrentYear();
            if (yearsFromNow > 0) {
                annualExpenses = Math.round(annualExpenses * Math.pow(1.0 + inflationRate, yearsFromNow));
            }
        }

        if (context.getProfile() != null && context.getInsurancePolicies() != null) {
            List<InsurancePolicyDto> policies = context.getInsurancePolicies();
            for (var policy : policies) {
                if (policy.annualPremium() != null) {
                    long premium = policy.annualPremium();
                    int activeMonths = 12;

                    if (policy.endDate() != null) {
                        if (policy.endDate().getYear() < year) {
                            continue;
                        } else if (policy.endDate().getYear() == year) {
                            activeMonths = policy.endDate().getMonthValue();
                        }
                    }

                    if (monthsToAdd < 12) {
                        activeMonths = Math.min(activeMonths, monthsToAdd);
                    }

                    if (activeMonths < 12) {
                        premium = Math.round((premium / 12.0) * activeMonths);
                    }

                    annualExpenses += premium;
                }
            }
        }
        for (LifeEvent event : lifeEvents) {
            int eventStartYear = event.getStartYearMonth().getYear();
            int eventEndYear = event.getEndYearMonth().getYear();

            if (year >= eventStartYear && year <= eventEndYear) {
                long eventCost = event.getCostPerOccurrence();
                if (inflationRate > 0) {
                    int yearsFromNow = year - context.getCurrentYear();
                    if (yearsFromNow > 0) {
                        eventCost = Math.round(eventCost * Math.pow(1.0 + inflationRate, yearsFromNow));
                    }
                }

                switch (event.getFrequency()) {
                    case ONE_TIME -> {
                        if (year == eventStartYear) {
                            boolean isTransitionYear = (year == context.getCurrentYear());
                            boolean alreadyOccurred = isTransitionYear
                                    && (event.getStartYearMonth().getMonthValue() < currentMonth);
                            if (!alreadyOccurred) {
                                annualExpenses += eventCost;
                            }
                        }
                    }
                    case YEARLY, MONTHLY -> {
                        int startMonth = (year == eventStartYear) ? event.getStartYearMonth().getMonthValue() : 1;
                        if (monthsToAdd < 12 && year == context.getCurrentYear()) {
                            startMonth = Math.max(startMonth, currentMonth);
                        }
                        int endMonth = (year == eventEndYear) ? event.getEndYearMonth().getMonthValue() : 12;
                        int months = endMonth - startMonth + 1;
                        if (months > 0) {
                            annualExpenses += (event.getFrequency() == LifeEventFrequency.YEARLY)
                                    ? Math.round((eventCost / 12.0) * months)
                                    : eventCost * months;
                        }
                    }
                }
            }
        }

        // Add education expenses
        if (context.getChildren() != null && !context.getChildren().isEmpty()) {
            for (ChildProfile child : context.getChildren()) {
                if (child == null || child.getId() == null) {
                    continue;
                }
                long eduCost = context.getEducationCost(child.getId(), year);
                if (eduCost > 0) {
                    if (inflationRate > 0) {
                        int yearsFromNow = year - context.getCurrentYear();
                        if (yearsFromNow > 0) {
                            eduCost = Math.round(eduCost * Math.pow(1.0 + inflationRate, yearsFromNow));
                        }
                    }
                    // Education cost for transition year is already accurately pre-calculated by
                    // EducationExpenseCalculator (filtering events >= currentMonth and prorating
                    // stage costs),
                    // so no erroneous linear scaling (monthsToAdd / 12.0) should be applied here.
                    annualExpenses += eduCost;
                }
            }
        }

        return annualExpenses;
    }

    public long calculateDynamicBaselineMonthlyExpenses(FinanceHubConfig config, UserProfile profile, String userId) {
        int pastMonths = Optional.ofNullable(config.getExpenseCalculationPastMonths()).orElse(12);
        if (pastMonths <= 0) {
            Long manualBase = profile != null ? profile.getManualMonthlyBaseExpense() : null;
            return manualBase != null ? manualBase : 250_000L;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(pastMonths);

        List<BankTransaction> bankTransactions = bankTransactionRepository
                .findByOwnerUserIdAndTransactionDateBetween(userId, startDate, endDate);
        List<CreditCardTransaction> ccTransactions = creditCardTransactionRepository
                .findByOwnerUserIdAndTransactionDateBetween(userId, startDate, endDate);

        LocalDate earliestDate = endDate;
        for (BankTransaction bt : bankTransactions) {
            if (bt.getTransactionDate() != null && bt.getTransactionDate().isBefore(earliestDate)) {
                earliestDate = bt.getTransactionDate();
            }
        }
        for (CreditCardTransaction cc : ccTransactions) {
            if (cc.getTransactionDate() != null && cc.getTransactionDate().isBefore(earliestDate)) {
                earliestDate = cc.getTransactionDate();
            }
        }
        long actualMonths = ChronoUnit.MONTHS.between(earliestDate.withDayOfMonth(1),
                endDate.withDayOfMonth(1));
        if (actualMonths <= 0) {
            actualMonths = 1;
        }
        long divisorMonths = Math.min(pastMonths, actualMonths);

        List<Long> ccTransactionIds = ccTransactions.stream().map(cc -> cc.getId()).toList();
        List<TransactionLink> links = ccTransactionIds.isEmpty() ? List.of()
                : transactionLinkRepository.findByCreditCardTransactionIdIn(ccTransactionIds);

        Map<Long, List<TransactionLink>> linksByCcId = links.stream()
                .collect(Collectors.groupingBy(link -> link.getCreditCardTransaction().getId()));

        long totalEssentialExpenses = 0L;

        for (BankTransaction bt : bankTransactions) {
            if (bt.getCategory() != null && bt.getCategory().isEssential() && !bt.getCategory().isSimulationDuplicate()
                    && bt.getWithdrawalAmount() != null) {
                totalEssentialExpenses += bt.getWithdrawalAmount();
            }
        }

        for (CreditCardTransaction cc : ccTransactions) {
            List<TransactionLink> ccLinks = linksByCcId.get(cc.getId());
            if (ccLinks != null && !ccLinks.isEmpty()) {
                for (TransactionLink link : ccLinks) {
                    if (link.getAmazonOrder() != null && link.getAmazonOrder().getItems() != null) {
                        for (var item : link.getAmazonOrder().getItems()) {
                            if (item.getCategory() != null && item.getCategory().isEssential()
                                    && !item.getCategory().isSimulationDuplicate()) {
                                if (item.getUnitPrice() != null && item.getQuantity() != null) {
                                    long itemTotal = item.getUnitPrice()
                                            .multiply(new BigDecimal(item.getQuantity())).longValue();
                                    totalEssentialExpenses += itemTotal;
                                }
                            }
                        }
                    }
                }
            } else {
                if (cc.getCategory() != null && cc.getCategory().isEssential()
                        && !cc.getCategory().isSimulationDuplicate() && cc.getAmount() != null) {
                    totalEssentialExpenses += cc.getAmount();
                }
            }
        }

        long baselineMonthlyExpenses = totalEssentialExpenses / divisorMonths;
        if (baselineMonthlyExpenses == 0) {
            baselineMonthlyExpenses = 250_000L; // Fallback if no essential data is found
            if (profile != null && profile.getManualMonthlyBaseExpense() != null) {
                baselineMonthlyExpenses = profile.getManualMonthlyBaseExpense();
            }
        }
        return baselineMonthlyExpenses;
    }
}
