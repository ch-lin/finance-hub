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

import ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.FitnessEvaluator;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper.ChromosomeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory responsible for creating request-scoped, immutable {@link AssetSimulationFitnessEvaluator} instances.
 */
@Component
@RequiredArgsConstructor
public class AssetSimulationFitnessEvaluatorFactory {

    private final AssetSimulationEngine assetSimulationEngine;
    private final ChromosomeMapper chromosomeMapper;

    public FitnessEvaluator create(SimulationData baseData, int targetAge) {
        return new AssetSimulationFitnessEvaluator(assetSimulationEngine, chromosomeMapper, baseData, targetAge);
    }
}
