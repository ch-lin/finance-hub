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
package ch.lin.finance.hub.backend.api.controller.simulation.dto;

import org.mapstruct.Mapper;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.GlobalOptimizationCommand;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.OptimizationConfig;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.OptimizationStrategy;

@Mapper(componentModel = "spring", uses = { AssetSimulationMapper.class })
public interface GlobalOptimizerMapper {

    @org.mapstruct.Mapping(target = "baseOverrides", ignore = true)
    GlobalOptimizationCommand toCommand(GlobalOptimizerRequest request);

    OptimizationConfig toConfig(OptimizationConfigRequest request);

    OptimizationStrategy toStrategy(OptimizationStrategyRequest request);

    /**
     * Converts SimulationOverrides to the API response DTO.
     * mortgageEarlyRepaymentYear is a Display-Only field: it surfaces the year
     * extracted from the mortgageEarlyRepayments map when Grid Search encodes a
     * full payoff via Long.MAX_VALUE, so the frontend never sees a raw MAX_VALUE.
     */
    default GlobalOptimizerResponse toResponse(SimulationOverrides overrides) {
        if (overrides == null) {
            return null;
        }
        Integer displayYear = null;
        if (overrides.mortgageEarlyRepayments() != null) {
            displayYear = overrides.mortgageEarlyRepayments().values().stream()
                    .filter(java.util.Objects::nonNull)
                    .flatMap(l -> l.stream())
                    .filter(java.util.Objects::nonNull)
                    .map(o -> o.year())
                    .findFirst()
                    .orElse(null);
        }
        return new GlobalOptimizerResponse(
                overrides.testNisaGrowthMonthlyContribution(),
                overrides.testNisaTsumitateMonthlyContribution(),
                displayYear,
                overrides.targetRetirementAgeOverride(),
                overrides.pensionStartAgeOverride(),
                overrides.idecoStartAgeOverride(),
                mapMortgageEarlyRepayments(overrides.mortgageEarlyRepayments()),
                overrides.fundOverrides() != null
                        ? overrides.fundOverrides().stream().map(this::toFundOverrideResponse).toList()
                        : null,
                mapMortgageRateScenarios(overrides.mortgageRateScenarios()),
                toProfileBaselineUsageDetailsResponse(overrides.appliedProfileBaselineDetails()));
    }

    default GlobalOptimizerResponse.ProfileBaselineUsageDetailsResponse toProfileBaselineUsageDetailsResponse(
            SimulationOverrides.ProfileBaselineUsageDetails details) {
        if (details == null) {
            return null;
        }
        return new GlobalOptimizerResponse.ProfileBaselineUsageDetailsResponse(
                details.targetRetirementAge(),
                details.pensionStartAge(),
                details.idecoStartAge(),
                details.nisaGrowthMonthlyContribution(),
                details.nisaTsumitateMonthlyContribution());
    }

    GlobalOptimizerResponse.MortgageEarlyRepaymentResponse toMortgageEarlyRepaymentResponse(
            SimulationOverrides.MortgageEarlyRepaymentOverride override);

    GlobalOptimizerResponse.FundOverrideResponse toFundOverrideResponse(SimulationOverrides.FundOverride override);

    GlobalOptimizerResponse.MortgageRateScenarioResponse toMortgageRateScenarioResponse(
            SimulationOverrides.MortgageRateScenarioOverride override);

    java.util.Map<Long, java.util.List<GlobalOptimizerResponse.MortgageEarlyRepaymentResponse>> mapMortgageEarlyRepayments(
            java.util.Map<Long, java.util.List<SimulationOverrides.MortgageEarlyRepaymentOverride>> value);

    java.util.List<GlobalOptimizerResponse.MortgageEarlyRepaymentResponse> mapMortgageEarlyRepaymentList(
            java.util.List<SimulationOverrides.MortgageEarlyRepaymentOverride> value);

    default java.util.Map<Long, java.util.List<GlobalOptimizerResponse.MortgageRateScenarioResponse>> mapMortgageRateScenarios(
            java.util.Map<Long, java.util.List<SimulationOverrides.MortgageRateScenarioOverride>> value) {
        if (value == null) {
            return null;
        }
        java.util.Map<Long, java.util.List<GlobalOptimizerResponse.MortgageRateScenarioResponse>> map = new java.util.HashMap<>();
        for (java.util.Map.Entry<Long, java.util.List<SimulationOverrides.MortgageRateScenarioOverride>> entry : value
                .entrySet()) {
            if (entry.getValue() != null) {
                map.put(entry.getKey(), entry.getValue().stream()
                        .map(this::toMortgageRateScenarioResponse)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }
        return map;
    }
}
