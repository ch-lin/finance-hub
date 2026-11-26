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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.evaluator;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.SimulationParameterResolver;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.FitnessEvaluator;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizationConstants;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper.ChromosomeMapper;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AssetSimulationFitnessEvaluator implements FitnessEvaluator {

    // A massive negative base score (death penalty) applied to any plan that
    // results in bankruptcy.
    // This guarantees that any solvent plan will strictly outscore a bankrupt plan,
    // preventing the
    // optimizer from recommending plans that run out of cash.
    private static final long BANKRUPTCY_DEATH_PENALTY_BASE = -1_000_000_000_000L;

    private final AssetSimulationEngine assetSimulationEngine;
    private final ChromosomeMapper chromosomeMapper;
    private final SimulationData baseData;
    private final int targetAge;

    @Override
    public double evaluate(Chromosome chromosome) {

        SimulationOverrides overrides = chromosomeMapper.toOverrides(chromosome);

        SimulationData contextClone;
        try {
            contextClone = baseData.cloneContext();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Failed to clone SimulationContext", e);
        }

        // Defensive clamp as safety net to prevent invalid age combinations from crashing simulation
        if (contextClone != null && contextClone.getBaseContext() != null) {
            int currentAge = contextClone.getBaseContext().getCurrentAge();
            Integer rawRetire = overrides != null ? overrides.targetRetirementAgeOverride() : null;
            Integer safeRetire = rawRetire != null ? Math.max(currentAge, rawRetire) : null;

            int effectiveRetire = SimulationParameterResolver.resolveRetirementAge(
                    contextClone.getBaseContext().getProfile(),
                    contextClone.getBaseContext().getConfig(),
                    safeRetire);

            Integer rawPension = overrides != null ? overrides.pensionStartAgeOverride() : null;
            int effectivePension = SimulationParameterResolver.resolvePensionStartAge(
                    contextClone.getBaseContext().getProfile(),
                    contextClone.getBaseContext().getConfig(),
                    rawPension);

            int minPensionBound = Math.max(OptimizationConstants.MIN_PENSION_AGE, effectiveRetire);
            Integer safePension = rawPension;
            if (effectivePension < minPensionBound) {
                safePension = minPensionBound;
            }

            if ((rawRetire != null && !rawRetire.equals(safeRetire))
                    || (safePension != null && !safePension.equals(rawPension))) {
                overrides = new SimulationOverrides(
                        overrides != null ? overrides.testNisaGrowthMonthlyContribution() : null,
                        overrides != null ? overrides.testNisaTsumitateMonthlyContribution() : null,
                        safeRetire,
                        safePension,
                        overrides != null ? overrides.idecoStartAgeOverride() : null,
                        overrides != null ? overrides.mortgageEarlyRepayments() : null,
                        overrides != null ? overrides.fundOverrides() : null,
                        overrides != null ? overrides.mortgageRateScenarios() : null,
                        overrides != null ? overrides.appliedProfileBaselineDetails() : null);
            }
        }

        List<AssetSimulationYearlyResult> simulation = assetSimulationEngine.runSimulationWithData(contextClone,
                targetAge,
                overrides);

        if (simulation.isEmpty()) {
            return -999_999_999.0;
        }

        AssetSimulationYearlyResult finalYear = simulation.get(simulation.size() - 1);
        double finalNetWorth = finalYear.getTrueNetWorth();
        double deficit = 0;

        for (AssetSimulationYearlyResult yearly : simulation) {
            if (yearly.getProjectedLiquidCash() < 0) {
                // Accumulate bankruptcy deficit
                deficit += Math.abs(yearly.getProjectedLiquidCash());
            }
        }

        if (deficit > 0) {
            // If there is any bankruptcy, return a massive negative score (hard
            // disqualifier) minus the deficit
            return BANKRUPTCY_DEATH_PENALTY_BASE - deficit;
        }

        return finalNetWorth;
    }
}
