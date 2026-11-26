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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.GlobalOptimizerService;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.GlobalOptimizationCommand;
import ch.lin.finance.hub.backend.api.controller.simulation.dto.GlobalOptimizerMapper;
import ch.lin.finance.hub.backend.api.controller.simulation.dto.GlobalOptimizerRequest;
import ch.lin.finance.hub.backend.api.controller.simulation.dto.GlobalOptimizerResponse;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/simulation/optimizer")
public class GlobalOptimizerController {

    private final GlobalOptimizerService globalOptimizerService;
    private final GlobalOptimizerMapper globalOptimizerMapper;

    @Autowired
    public GlobalOptimizerController(GlobalOptimizerService globalOptimizerService,
            GlobalOptimizerMapper globalOptimizerMapper) {
        this.globalOptimizerService = globalOptimizerService;
        this.globalOptimizerMapper = globalOptimizerMapper;
    }

    @PostMapping("/{targetAge}")
    public ResponseEntity<ApiResponse<GlobalOptimizerResponse>> optimize(
            @PathVariable int targetAge,
            @Valid @RequestBody GlobalOptimizerRequest request) {

        GlobalOptimizationCommand command = globalOptimizerMapper.toCommand(request);
        SimulationOverrides optimalOverrides = globalOptimizerService.optimize(targetAge, command);
        GlobalOptimizerResponse response = globalOptimizerMapper.toResponse(optimalOverrides);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
