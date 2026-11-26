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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.dependent.DependentDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;

@Service
public class DependentDeductionEngine {

    private final DependentDeductionStrategyFactory strategyFactory;

    public DependentDeductionEngine() {
        this(new DependentDeductionStrategyFactory());
    }

    @Autowired
    public DependentDeductionEngine(DependentDeductionStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    public long calculateIncomeTaxDeduction(SimulationContext context, Dependent dependent,
            LocalDate simulationYearEnd) {
        if (dependent == null) {
            return 0L;
        }
        int year = resolveYear(context, simulationYearEnd);
        return strategyFactory.getIncomeTaxStrategy(year).calculateIncomeTaxDeduction(dependent, simulationYearEnd);
    }

    public long calculateIncomeTaxDeduction(Dependent dependent, LocalDate simulationYearEnd) {
        return calculateIncomeTaxDeduction(null, dependent, simulationYearEnd);
    }

    public long calculateInhabitantTaxDeduction(SimulationContext context, Dependent dependent,
            LocalDate simulationYearEnd) {
        if (dependent == null) {
            return 0L;
        }
        int year = resolveYear(context, simulationYearEnd);
        return strategyFactory.getInhabitantTaxStrategy(year).calculateInhabitantTaxDeduction(dependent, simulationYearEnd);
    }

    public long calculateInhabitantTaxDeduction(Dependent dependent, LocalDate simulationYearEnd) {
        return calculateInhabitantTaxDeduction(null, dependent, simulationYearEnd);
    }

    private int resolveYear(SimulationContext context, LocalDate simulationYearEnd) {
        if (simulationYearEnd != null) {
            return simulationYearEnd.getYear();
        }
        if (context != null && context.getYear() > 0) {
            return context.getYear();
        }
        return 2026;
    }
}
