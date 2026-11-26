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
package ch.lin.finance.hub.backend.api.controller.simulation;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngine;
import ch.lin.finance.hub.backend.api.controller.simulation.dto.AssetSimulationMapper;
import ch.lin.finance.hub.backend.api.controller.simulation.dto.AssetSimulationRequest;
import ch.lin.finance.hub.backend.api.controller.simulation.dto.AssetSimulationYearlyResponse;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/simulation/asset-simulation")
public class AssetSimulationController {

    private final AssetSimulationEngine assetSimulationEngine;
    private final AssetSimulationMapper assetSimulationMapper;

    @Autowired
    public AssetSimulationController(AssetSimulationEngine assetSimulationEngine,
            AssetSimulationMapper assetSimulationMapper) {
        this.assetSimulationEngine = assetSimulationEngine;
        this.assetSimulationMapper = assetSimulationMapper;
    }

    @PostMapping("/{targetAge}")
    public ResponseEntity<ApiResponse<List<AssetSimulationYearlyResponse>>> runSimulation(@PathVariable int targetAge,
            @Valid @RequestBody(required = false) AssetSimulationRequest request) {

        SimulationOverrides overrides;
        if (request != null) {
            overrides = assetSimulationMapper.toOverrides(request);
        } else {
            overrides = new SimulationOverrides(null, null, null, null, null, null, null, null, null);
        }

        List<AssetSimulationYearlyResponse> simulation = assetSimulationEngine.runSimulationWithOverrides(
                targetAge, overrides).stream().map(assetSimulationMapper::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(simulation));
    }
}
