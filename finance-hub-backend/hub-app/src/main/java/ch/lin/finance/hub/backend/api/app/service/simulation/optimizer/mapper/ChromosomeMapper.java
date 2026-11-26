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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome.MortgageRepaymentGene;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;

@Component
public class ChromosomeMapper {

    public SimulationOverrides toOverrides(Chromosome chromosome) {

        Map<Long, List<SimulationOverrides.MortgageEarlyRepaymentOverride>> mortgageOverrides = new HashMap<>();
        boolean hasComplexMortgages = false;

        if (chromosome.getMortgageRepayments() != null) {
            for (Map.Entry<Long, List<MortgageRepaymentGene>> entry : chromosome.getMortgageRepayments().entrySet()) {
                List<SimulationOverrides.MortgageEarlyRepaymentOverride> overrides = new ArrayList<>();
                for (MortgageRepaymentGene gene : entry.getValue()) {
                    if (gene.getYear() != null && gene.getAmount() != null) {
                        overrides.add(new SimulationOverrides.MortgageEarlyRepaymentOverride(
                                gene.getYear(),
                                gene.getAmount(),
                                gene.getRepaymentType() != null ? gene.getRepaymentType() : EarlyRepaymentType.REDUCE_TERM));
                        hasComplexMortgages = true;
                    }
                }
                mortgageOverrides.put(entry.getKey(), overrides);
            }
        }

        Integer legacyYear;
        if (!hasComplexMortgages && chromosome.getGridSingleMortgageRepaymentYear() != null) {
            legacyYear = chromosome.getGridSingleMortgageRepaymentYear();
            // Bridge Grid Search to Feature A: convert the single payoff year into a
            // scheduled
            // full-payoff repayment (Long.MAX_VALUE) for every known mortgage.
            // The clamp guard in MortgageCalculator will truncate it to the actual balance.
            for (Long mortgageId : mortgageOverrides.keySet()) {
                mortgageOverrides.computeIfAbsent(mortgageId, k -> new ArrayList<>())
                        .add(new SimulationOverrides.MortgageEarlyRepaymentOverride(
                                legacyYear, Long.MAX_VALUE, EarlyRepaymentType.REDUCE_TERM));
            }
        }

        return new SimulationOverrides(
                chromosome.getNisaGrowthTarget(),
                chromosome.getNisaTsumitateTarget(),
                chromosome.getRetirementAge(),
                chromosome.getPensionStartAge(),
                chromosome.getIdecoStartAge(),
                mortgageOverrides,
                null, // fundOverrides
                null, // mortgageRateScenarios
                null // appliedProfileBaselineDetails
        );
    }
}
