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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;

@ExtendWith(MockitoExtension.class)
class GridSearchEngineTest {

    @Mock
    private FitnessEvaluator fitnessEvaluator;

    private GridSearchEngine gridSearchEngine;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        gridSearchEngine = new GridSearchEngine(fitnessEvaluator, null);
    }

    @Test
    void testOptimize_EvaluatesCombinations_AndReturnsBest() {
        // Seed with only NISA unlocked (231 combinations)
        Chromosome seed = new Chromosome();
        seed.setNisaGrowthTargetLocked(false);
        seed.setNisaTsumitateTargetLocked(false);
        seed.setRetirementAgeLocked(true);
        seed.setRetirementAge(65);
        seed.setPensionStartAgeLocked(true);
        seed.setPensionStartAge(65);
        seed.setIdecoStartAgeLocked(true);
        seed.setIdecoStartAge(60);
        seed.setMortgageRepaymentsLocked(true);

        when(fitnessEvaluator.evaluate(any())).thenAnswer(invocation -> {
            Chromosome c = invocation.getArgument(0);
            // Higher score for Growth=200,000 and Tsumitate=100,000
            if (c.getNisaGrowthTarget() != null && c.getNisaTsumitateTarget() != null &&
                    c.getNisaGrowthTarget() == 200_000L && c.getNisaTsumitateTarget() == 100_000L) {
                return 1000.0;
            }
            return 10.0;
        });

        Chromosome best = gridSearchEngine.optimize(seed);

        assertNotNull(best);
        assertEquals(200_000L, best.getNisaGrowthTarget());
        assertEquals(100_000L, best.getNisaTsumitateTarget());
    }

    @Test
    void testOptimize_ThrowsException_WhenCombinationsExceedHardCap() {
        // Seed with all variables unlocked (231 * 16 * 16 = 59,136 > 5000)
        Chromosome seed = new Chromosome();
        seed.setNisaGrowthTargetLocked(false);
        seed.setNisaTsumitateTargetLocked(false);
        seed.setRetirementAgeLocked(false);
        seed.setPensionStartAgeLocked(false);
        seed.setIdecoStartAgeLocked(false);
        seed.setMortgageRepaymentsLocked(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> gridSearchEngine.optimize(seed));
        assertNotNull(exception);
    }

    @Test
    void testOptimize_ClampsRetirementAgeToCurrentAge() {
        SimulationData data = new SimulationData();
        SimulationContext context = new SimulationContext();
        context.setCurrentAge(58);
        data.setBaseContext(context);

        GridSearchEngine engine = new GridSearchEngine(fitnessEvaluator, data);

        Chromosome seed = new Chromosome();
        seed.setNisaGrowthTargetLocked(true);
        seed.setNisaTsumitateTargetLocked(true);
        seed.setRetirementAgeLocked(false);
        seed.setPensionStartAgeLocked(true);
        seed.setPensionStartAge(75);
        seed.setIdecoStartAgeLocked(true);
        seed.setMortgageRepaymentsLocked(true);

        java.util.List<Integer> evaluatedRetirementAges = new java.util.ArrayList<>();
        when(fitnessEvaluator.evaluate(any())).thenAnswer(invocation -> {
            Chromosome c = invocation.getArgument(0);
            evaluatedRetirementAges.add(c.getRetirementAge());
            return 10.0;
        });

        engine.optimize(seed);

        assertTrue(!evaluatedRetirementAges.isEmpty());
        for (int age : evaluatedRetirementAges) {
            assertTrue(age >= 58, "Evaluated retirement age should never be less than currentAge (58), but was: " + age);
        }
    }

    @Test
    void testOptimize_ClampsPensionAgeToRetirementAgeAndMinPension() {
        SimulationData data = new SimulationData();
        SimulationContext context = new SimulationContext();
        context.setCurrentAge(65);
        data.setBaseContext(context);

        GridSearchEngine engine = new GridSearchEngine(fitnessEvaluator, data);

        Chromosome seed = new Chromosome();
        seed.setNisaGrowthTargetLocked(true);
        seed.setNisaTsumitateTargetLocked(true);
        seed.setRetirementAgeLocked(false);
        seed.setPensionStartAgeLocked(false);
        seed.setIdecoStartAgeLocked(true);
        seed.setMortgageRepaymentsLocked(true);

        java.util.List<Chromosome> evaluatedChromosomes = new java.util.ArrayList<>();
        when(fitnessEvaluator.evaluate(any())).thenAnswer(invocation -> {
            Chromosome c = invocation.getArgument(0);
            evaluatedChromosomes.add(new Chromosome(c));
            return 10.0;
        });

        engine.optimize(seed);

        assertTrue(!evaluatedChromosomes.isEmpty());
        for (Chromosome c : evaluatedChromosomes) {
            int retAge = c.getRetirementAge();
            int penAge = c.getPensionStartAge();
            assertTrue(penAge >= Math.max(OptimizationConstants.MIN_PENSION_AGE, retAge),
                    String.format("Pension age (%d) must be >= max(60, retire (%d))", penAge, retAge));
        }
    }
}
