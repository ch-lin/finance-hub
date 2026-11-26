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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.GlobalOptimizationCommand;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.OptimizationConfig;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.OptimizationStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.evaluator.AssetSimulationFitnessEvaluator;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.evaluator.AssetSimulationFitnessEvaluatorFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper.ChromosomeMapper;
import ch.lin.finance.hub.backend.api.common.exception.OptimizationNotSupportedException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

@ExtendWith(MockitoExtension.class)
public class GlobalOptimizerServiceTest {

    @Mock
    private AssetSimulationEngine assetSimulationEngine;

    @Mock
    private AssetSimulationFitnessEvaluatorFactory fitnessEvaluatorFactory;

    @Mock
    @SuppressWarnings("unused")
    private AssetSimulationFitnessEvaluator fitnessEvaluator;

    @Mock
    private ChromosomeMapper chromosomeMapper;

    @Mock
    @SuppressWarnings("unused")
    private NisaUserSettingsRepository nisaUserSettingsRepository;

    @InjectMocks
    private GlobalOptimizerServiceImpl optimizerService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        SimulationData mockData = new SimulationData();
        SimulationContext context = new SimulationContext();
        FinanceHubConfig config = new FinanceHubConfig("default");
        config.setTargetRetirementAge(65);
        context.setConfig(config);
        mockData.setBaseContext(context);

        when(assetSimulationEngine.fetchSimulationData()).thenReturn(mockData);
        org.mockito.Mockito.lenient().when(fitnessEvaluatorFactory.create(any(), anyInt())).thenReturn(fitnessEvaluator);
    }

    @Test
    void testOptimize_RunsGridSearch_WhenLowDimensional() {
        // Given AUTO strategy and only 1 unlocked variable (NISA target, 31 options)
        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, null, null, null, null, null, null,
                null);
        OptimizationConfig config = new OptimizationConfig(false, false, false, true, false);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.AUTO);

        when(chromosomeMapper.toOverrides(any())).thenReturn(baseOverrides);

        // When
        SimulationOverrides result = optimizerService.optimize(90, command);

        // Then
        assertNotNull(result);
        org.mockito.Mockito.verify(fitnessEvaluator, org.mockito.Mockito.times(231)).evaluate(any());
    }

    @Test
    void testOptimize_RunsGA_WhenHighDimensional() {
        // Given AUTO strategy and multiple unlocked variables (31 * 31 * 16 * 16 >
        // 1000)
        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, 65, 65, 60, null, null, null, null);
        OptimizationConfig config = new OptimizationConfig(true, true, true, true, false);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.AUTO);

        when(chromosomeMapper.toOverrides(any())).thenReturn(baseOverrides);

        // When
        SimulationOverrides result = optimizerService.optimize(90, command);

        // Then
        assertNotNull(result);
        org.mockito.Mockito.verify(fitnessEvaluator, org.mockito.Mockito.atLeast(100)).evaluate(any());
    }

    @Test
    void testOptimize_ThrowsException_WhenForceGridWithCombinationsExceedingCap() {
        // Given FORCE_GRID strategy and 4 unlocked variables (> 1000 combinations)
        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, 65, 65, 60, null, null, null, null);
        OptimizationConfig config = new OptimizationConfig(true, true, true, true, false);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.FORCE_GRID);

        // When / Then
        OptimizationNotSupportedException exception = assertThrows(OptimizationNotSupportedException.class,
                () -> optimizerService.optimize(90, command));
        assertNotNull(exception);
    }

    @Test
    void testOptimize_ThrowsException_WhenForceGridWithMortgageCombinationsExceedCap() {
        // Given FORCE_GRID strategy and mortgage unlocked, and mortgage options are
        // huge (e.g. 50 years)
        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, null, null, null, null, null, null,
                null);
        OptimizationConfig config = new OptimizationConfig(false, false, false, false, true);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.FORCE_GRID);

        SimulationData mockData = new SimulationData();
        SimulationContext context = new SimulationContext();
        FinanceHubConfig hubConfig = new FinanceHubConfig("default");
        hubConfig.setTargetRetirementAge(65);
        context.setConfig(hubConfig);
        context.setCurrentYear(2025);
        mockData.setBaseContext(context);

        // Mock a mortgage that takes 2000 years to repay to force combinations > 1000
        MortgageRecord m = org.mockito.Mockito.mock(MortgageRecord.class);
        org.mockito.Mockito.when(m.getStartDate()).thenReturn(java.time.LocalDate.of(2025, 1, 1));
        org.mockito.Mockito.when(m.getLoanTermMonths()).thenReturn(12 * 2000); // 2000 years, so combinations = 2003 >
                                                                               // 1000
        mockData.setAllMortgages(java.util.List.of(m));

        when(assetSimulationEngine.fetchSimulationData()).thenReturn(mockData);

        // When / Then
        OptimizationNotSupportedException exception = assertThrows(OptimizationNotSupportedException.class,
                () -> optimizerService.optimize(90, command));
        assertNotNull(exception);
    }

    @Test
    void testOptimize_Succeeds_WhenForceGridWithinCap() {
        // Given FORCE_GRID strategy and only NISA unlocked (31 combinations <= 1000)
        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, null, null, null, null, null, null,
                null);
        OptimizationConfig config = new OptimizationConfig(false, false, false, true, false);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.FORCE_GRID);

        when(chromosomeMapper.toOverrides(any())).thenReturn(baseOverrides);

        // When
        SimulationOverrides result = optimizerService.optimize(90, command);

        // Then
        assertNotNull(result);
    }

    @Test
    void testOptimize_ReturnsNullDetails_WhenAllConfigOptimized() {
        // Given all configs set to true
        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
                null);
        OptimizationConfig config = new OptimizationConfig(true, true, true, true, false);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.AUTO);

        SimulationData mockData = new SimulationData();
        SimulationContext context = new SimulationContext();
        FinanceHubConfig hubConfig = new FinanceHubConfig("default");
        context.setConfig(hubConfig);

        UserProfile profile = new UserProfile("test-user", LocalDate.of(1990, 1, 1), null, 60);
        profile.setApplyProfileToOptimizerBaseline(true);
        context.setProfile(profile);
        mockData.setBaseContext(context);
        when(assetSimulationEngine.fetchSimulationData()).thenReturn(mockData);
        when(chromosomeMapper.toOverrides(any())).thenReturn(baseOverrides);

        // When
        SimulationOverrides result = optimizerService.optimize(90, command);

        // Then
        assertNotNull(result);
        assertNull(result.appliedProfileBaselineDetails().targetRetirementAge());
        assertNull(result.appliedProfileBaselineDetails().pensionStartAge());
        assertNull(result.appliedProfileBaselineDetails().idecoStartAge());
        assertNull(result.appliedProfileBaselineDetails().nisaGrowthMonthlyContribution());
        assertNull(result.appliedProfileBaselineDetails().nisaTsumitateMonthlyContribution());
    }

    @Test
    void testOptimize_ReturnsAppliedValues_WhenConfigNotOptimizedAndProfileHasValue() {
        // Given retirement not optimized, and profile has value
        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
                null);
        OptimizationConfig config = new OptimizationConfig(false, true, true, true, false);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.AUTO);

        SimulationData mockData = new SimulationData();
        SimulationContext context = new SimulationContext();
        FinanceHubConfig hubConfig = new FinanceHubConfig("default");
        context.setConfig(hubConfig);

        UserProfile profile = new UserProfile("test-user", LocalDate.of(1990, 1, 1), null, 62);
        profile.setApplyProfileToOptimizerBaseline(true);
        context.setProfile(profile);
        mockData.setBaseContext(context);
        when(assetSimulationEngine.fetchSimulationData()).thenReturn(mockData);
        when(chromosomeMapper.toOverrides(any())).thenReturn(baseOverrides);

        // When
        SimulationOverrides result = optimizerService.optimize(90, command);

        // Then
        assertNotNull(result);
        assertEquals(62, result.appliedProfileBaselineDetails().targetRetirementAge());
        assertNull(result.appliedProfileBaselineDetails().pensionStartAge());
    }

    @Test
    void testOptimize_ReturnsNullDetails_WhenConfigNotOptimizedButProfileIsEmpty() {
        // Given retirement not optimized, but profile has no value (null)
        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
                null);
        OptimizationConfig config = new OptimizationConfig(false, true, true, true, false);
        GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                OptimizationStrategy.AUTO);

        SimulationData mockData = new SimulationData();
        SimulationContext context = new SimulationContext();
        FinanceHubConfig hubConfig = new FinanceHubConfig("default");
        context.setConfig(hubConfig);

        UserProfile profile = new UserProfile("test-user", LocalDate.of(1990, 1, 1), null, null);
        profile.setApplyProfileToOptimizerBaseline(true);
        context.setProfile(profile);
        mockData.setBaseContext(context);
        when(assetSimulationEngine.fetchSimulationData()).thenReturn(mockData);
        when(chromosomeMapper.toOverrides(any())).thenReturn(baseOverrides);

        // When
        SimulationOverrides result = optimizerService.optimize(90, command);

        // Then
        assertNotNull(result);
        assertNull(result.appliedProfileBaselineDetails().targetRetirementAge());
    }
}
