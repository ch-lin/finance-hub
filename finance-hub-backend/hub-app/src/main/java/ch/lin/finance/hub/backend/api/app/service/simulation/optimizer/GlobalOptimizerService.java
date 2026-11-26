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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.GlobalOptimizationCommand;

public interface GlobalOptimizerService {

    /**
     * Runs the GA engine to find the optimal simulation overrides for a given user
     * up to the target age.
     * 
     * @param targetAge The target age to optimize net worth for
     * @param command   The optimization configuration and constraints
     * @return The best SimulationOverrides found by the algorithm
     */
    SimulationOverrides optimize(int targetAge, GlobalOptimizationCommand command);
}
