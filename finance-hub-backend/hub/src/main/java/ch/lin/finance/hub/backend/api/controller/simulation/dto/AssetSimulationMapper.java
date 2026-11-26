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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;

@Mapper(componentModel = "spring")
public interface AssetSimulationMapper {

    @Mapping(target = "isRetired", source = "retired")
    AssetSimulationYearlyResponse toDto(AssetSimulationYearlyResult result);

    @Mapping(target = "appliedProfileBaselineDetails", ignore = true)
    SimulationOverrides toOverrides(AssetSimulationRequest request);

    SimulationOverrides.FundOverride toFundOverride(
            AssetSimulationRequest.FundOverrideRequest request);

    SimulationOverrides.MortgageRateScenarioOverride toMortgageRateScenarioOverride(
            AssetSimulationRequest.MortgageRateScenarioRequest request);

    @Mapping(target = "repaymentType", constant = "REDUCE_TERM")
    SimulationOverrides.MortgageEarlyRepaymentOverride toMortgageEarlyRepaymentOverride(
            AssetSimulationRequest.MortgageEarlyRepaymentRequest request);

    default Map<Long, List<SimulationOverrides.MortgageEarlyRepaymentOverride>> mapMortgageEarlyRepayments(
            Map<Long, List<AssetSimulationRequest.MortgageEarlyRepaymentRequest>> value) {
        if (value == null) {
            return null;
        }
        Map<Long, List<SimulationOverrides.MortgageEarlyRepaymentOverride>> map = new HashMap<>();
        for (Map.Entry<Long, List<AssetSimulationRequest.MortgageEarlyRepaymentRequest>> entry : value
                .entrySet()) {
            if (entry.getValue() != null) {
                map.put(entry.getKey(), entry.getValue().stream()
                        .map(this::toMortgageEarlyRepaymentOverride)
                        .toList());
            } else {
                map.put(entry.getKey(), null);
            }
        }
        return map;
    }

    default Map<Long, List<SimulationOverrides.MortgageRateScenarioOverride>> mapMortgageRateScenarios(
            Map<Long, List<AssetSimulationRequest.MortgageRateScenarioRequest>> value) {
        if (value == null) {
            return null;
        }
        Map<Long, List<SimulationOverrides.MortgageRateScenarioOverride>> map = new HashMap<>();
        for (Map.Entry<Long, List<AssetSimulationRequest.MortgageRateScenarioRequest>> entry : value
                .entrySet()) {
            if (entry.getValue() != null) {
                map.put(entry.getKey(), entry.getValue().stream()
                        .map(this::toMortgageRateScenarioOverride)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }
        return map;
    }
}
