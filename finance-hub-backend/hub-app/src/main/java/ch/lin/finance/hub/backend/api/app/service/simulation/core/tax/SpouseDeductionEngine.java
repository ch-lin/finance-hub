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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.spouse.SpouseDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;

@Service
public class SpouseDeductionEngine {

    private final SpouseDeductionStrategyFactory strategyFactory;

    public SpouseDeductionEngine() {
        this(new SpouseDeductionStrategyFactory());
    }

    @Autowired
    public SpouseDeductionEngine(SpouseDeductionStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    public long calculateIncomeTaxDeduction(long userGrossIncome, Dependent spouse) {
        return calculateIncomeTaxDeduction(userGrossIncome, spouse, null);
    }

    public long calculateIncomeTaxDeduction(long userGrossIncome, Dependent spouse, LocalDate simulationYearEnd) {
        if (spouse == null)
            return 0L;

        Long annualIncome = spouse.getEstimatedAnnualIncome();
        long spouseIncome = annualIncome != null ? annualIncome : 0L;

        boolean isElderly = false;
        if (spouse.getBirthDate() != null && simulationYearEnd != null) {
            isElderly = ChronoUnit.YEARS.between(spouse.getBirthDate(), simulationYearEnd) >= 70;
        }

        int year = simulationYearEnd != null ? simulationYearEnd.getYear() : 2026;
        return strategyFactory.getIncomeTaxStrategy(year).calculateIncomeTaxDeduction(userGrossIncome, spouseIncome, isElderly);
    }

    public long calculateInhabitantTaxDeduction(long userGrossIncome, Dependent spouse) {
        return calculateInhabitantTaxDeduction(userGrossIncome, spouse, null);
    }

    public long calculateInhabitantTaxDeduction(long userGrossIncome, Dependent spouse, LocalDate simulationYearEnd) {
        if (spouse == null)
            return 0L;

        Long annualIncome = spouse.getEstimatedAnnualIncome();
        long spouseIncome = annualIncome != null ? annualIncome : 0L;

        boolean isElderly = false;
        if (spouse.getBirthDate() != null && simulationYearEnd != null) {
            isElderly = ChronoUnit.YEARS.between(spouse.getBirthDate(), simulationYearEnd) >= 70;
        }

        int year = simulationYearEnd != null ? simulationYearEnd.getYear() : 2026;
        return strategyFactory.getInhabitantTaxStrategy(year).calculateInhabitantTaxDeduction(userGrossIncome, spouseIncome, isElderly);
    }
}

