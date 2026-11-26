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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper.ChromosomeMapper;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

@ExtendWith(MockitoExtension.class)
class AssetSimulationFitnessEvaluatorTest {

    @Mock
    private AssetSimulationEngine assetSimulationEngine;

    @Mock
    private ChromosomeMapper chromosomeMapper;

    private SimulationData simulationData;
    private AssetSimulationFitnessEvaluator evaluator;

    @BeforeEach
    void setUp() {
        simulationData = new SimulationData();
        SimulationContext context = new SimulationContext();
        context.setCurrentAge(55);
        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setTargetRetirementAge(65);
        config.setPensionStartAge(65);
        context.setConfig(config);
        simulationData.setBaseContext(context);

        evaluator = new AssetSimulationFitnessEvaluator(assetSimulationEngine, chromosomeMapper, simulationData, 85);
    }

    @Test
    void shouldClampRetirementAndPensionAge_WhenInvalidCombinationIsGiven() {
        Chromosome chromosome = new Chromosome();
        chromosome.setRetirementAge(50); // Less than currentAge (55)
        chromosome.setPensionStartAge(50); // Less than retirementAge and less than MIN_PENSION_AGE (60)

        SimulationOverrides rawOverrides = new SimulationOverrides(
                null, null, 50, 50, null, null, null, null, null);
        when(chromosomeMapper.toOverrides(chromosome)).thenReturn(rawOverrides);

        AssetSimulationYearlyResult dummyResult = new AssetSimulationYearlyResult();
        dummyResult.setTrueNetWorth(50000000L);
        dummyResult.setProjectedLiquidCash(10000000L);
        when(assetSimulationEngine.runSimulationWithData(any(), anyInt(), any()))
                .thenReturn(List.of(dummyResult));

        double score = evaluator.evaluate(chromosome);
        assertEquals(50000000.0, score);

        ArgumentCaptor<SimulationOverrides> captor = ArgumentCaptor.forClass(SimulationOverrides.class);
        verify(assetSimulationEngine).runSimulationWithData(any(), anyInt(), captor.capture());

        SimulationOverrides captured = captor.getValue();
        assertNotNull(captured);
        assertEquals(55, captured.targetRetirementAgeOverride(), "Retirement age should be clamped to currentAge (55)");
        assertEquals(60, captured.pensionStartAgeOverride(), "Pension start age should be clamped to max(MIN_PENSION_AGE=60, retirementAge=55)");
    }

    @Test
    void shouldClampPensionAge_WhenRetirementAgeIsLaterThanPensionAge() {
        Chromosome chromosome = new Chromosome();
        chromosome.setRetirementAge(68);
        chromosome.setPensionStartAge(60); // Earlier than retirementAge 68

        SimulationOverrides rawOverrides = new SimulationOverrides(
                null, null, 68, 60, null, null, null, null, null);
        when(chromosomeMapper.toOverrides(chromosome)).thenReturn(rawOverrides);

        AssetSimulationYearlyResult dummyResult = new AssetSimulationYearlyResult();
        dummyResult.setTrueNetWorth(60000000L);
        dummyResult.setProjectedLiquidCash(15000000L);
        when(assetSimulationEngine.runSimulationWithData(any(), anyInt(), any()))
                .thenReturn(List.of(dummyResult));

        double score = evaluator.evaluate(chromosome);
        assertEquals(60000000.0, score);

        ArgumentCaptor<SimulationOverrides> captor = ArgumentCaptor.forClass(SimulationOverrides.class);
        verify(assetSimulationEngine).runSimulationWithData(any(), anyInt(), captor.capture());

        SimulationOverrides captured = captor.getValue();
        assertNotNull(captured);
        assertEquals(68, captured.targetRetirementAgeOverride());
        assertEquals(68, captured.pensionStartAgeOverride(), "Pension start age should be clamped up to retirementAge (68)");
    }
}
