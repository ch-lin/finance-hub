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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.FitnessEvaluator;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper.ChromosomeMapper;

@ExtendWith(MockitoExtension.class)
class AssetSimulationFitnessEvaluatorFactoryTest {

    @Mock
    private AssetSimulationEngine assetSimulationEngine;

    @Mock
    private ChromosomeMapper chromosomeMapper;

    @InjectMocks
    private AssetSimulationFitnessEvaluatorFactory factory;

    @Test
    void shouldCreateEvaluatorInstance() {
        SimulationData data = new SimulationData();
        FitnessEvaluator evaluator = factory.create(data, 65);

        assertNotNull(evaluator);
        assertTrue(evaluator instanceof AssetSimulationFitnessEvaluator);
    }
}
