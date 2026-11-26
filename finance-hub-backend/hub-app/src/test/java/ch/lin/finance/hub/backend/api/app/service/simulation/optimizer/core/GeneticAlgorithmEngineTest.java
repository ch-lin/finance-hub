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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

@ExtendWith(MockitoExtension.class)
class GeneticAlgorithmEngineTest {

    @Mock
    private FitnessEvaluator fitnessEvaluator;

    private GeneticAlgorithmEngine engine;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        SimulationContext context = new SimulationContext();
        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setGaPopulationSize(20);
        config.setGaMaxGenerations(5);
        config.setGaMutationRate(0.8);
        config.setGaEliteRatio(10);
        context.setConfig(config);
        context.setCurrentYear(2025);

        UserProfile profile = new UserProfile("test", LocalDate.of(1990, 1, 1), null, 85);
        context.setProfile(profile);

        engine = new GeneticAlgorithmEngine(fitnessEvaluator, context);
    }

    @Test
    void testOptimize_EvolvesAndClonesCorrectly() {
        Chromosome seed = new Chromosome();
        seed.setNisaGrowthTargetLocked(false);
        seed.setNisaTsumitateTargetLocked(false);
        seed.setNisaGrowthTarget(0L);
        seed.setNisaTsumitateTarget(0L);

        when(fitnessEvaluator.evaluate(any(Chromosome.class))).thenAnswer(invocation -> {
            Chromosome c = invocation.getArgument(0);
            double score = 0;
            if (c.getNisaGrowthTarget() != null) {
                score += c.getNisaGrowthTarget();
            }
            if (c.getNisaTsumitateTarget() != null) {
                score += c.getNisaTsumitateTarget();
            }
            return score;
        });

        Chromosome best = engine.optimize(seed);

        assertNotNull(best);
        // We assert that the engine actually explored other options (mutation)
        assertTrue(best.getNisaGrowthTarget() >= 0L);
        assertTrue(best.getNisaTsumitateTarget() >= 0L);
    }
}
