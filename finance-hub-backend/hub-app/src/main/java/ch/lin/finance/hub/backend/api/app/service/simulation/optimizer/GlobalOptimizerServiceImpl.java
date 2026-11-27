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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.AssetSimulationEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.GlobalOptimizationCommand;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.OptimizationConfig;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.OptimizationStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome.MortgageRepaymentGene;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.GeneticAlgorithmEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.FitnessEvaluator;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.GridSearchEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizationConstants;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizationUtils;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizerEngine;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.evaluator.AssetSimulationFitnessEvaluatorFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper.ChromosomeMapper;
import ch.lin.finance.hub.backend.api.common.exception.OptimizationNotSupportedException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaUserSettings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {

    private final AssetSimulationEngine assetSimulationEngine;
    private final AssetSimulationFitnessEvaluatorFactory fitnessEvaluatorFactory;
    private final ChromosomeMapper chromosomeMapper;
    private final NisaUserSettingsRepository nisaUserSettingsRepository;

    @Override
    public SimulationOverrides optimize(int targetAge, GlobalOptimizationCommand command) {
        // 1. Fetch simulation context (the universe state)
        SimulationData baseData = assetSimulationEngine.fetchSimulationData();

        // 2. Create request-scoped Fitness Evaluator with base data
        FitnessEvaluator fitnessEvaluator = fitnessEvaluatorFactory.create(baseData, targetAge);

        // 3. Construct Initial Chromosome (Seed) with Locks
        Chromosome seed = buildSeedChromosome(baseData, command);

        // 4. Determine Strategy & Run Engine
        OptimizerEngine engine;
        OptimizationStrategy strategy = (command != null && command.strategy() != null)
                ? command.strategy()
                : OptimizationStrategy.AUTO;

        switch (strategy) {
            case FORCE_GA:
                engine = new GeneticAlgorithmEngine(fitnessEvaluator, baseData.getBaseContext());
                break;
            case FORCE_GRID:
                if (!seed.isMortgageRepaymentsLocked()
                        && baseData.getAllMortgages() != null
                        && baseData.getAllMortgages().size() > 1) {
                    throw new OptimizationNotSupportedException(
                            "Grid Search strategy only supports a single mortgage. Use AUTO or FORCE_GA for multiple mortgages.");
                }
                long gridCombinations = calculateSearchSpace(seed, baseData, strategy);
                if (gridCombinations > OptimizationConstants.MAX_GRID_SEARCH_SPACE) {
                    throw new OptimizationNotSupportedException(
                            "Search space (" + gridCombinations + ") exceeds maximum limit ("
                                    + OptimizationConstants.MAX_GRID_SEARCH_SPACE + ") for FORCE_GRID strategy.");
                }
                engine = new GridSearchEngine(fitnessEvaluator, baseData);
                break;
            case AUTO:
            default:
                long combinations = calculateSearchSpace(seed, baseData, strategy);
                if (combinations <= OptimizationConstants.MAX_GRID_SEARCH_SPACE) {
                    engine = new GridSearchEngine(fitnessEvaluator, baseData);
                } else {
                    engine = new GeneticAlgorithmEngine(fitnessEvaluator, baseData.getBaseContext());
                }
                break;
        }

        Chromosome bestSolution = engine.optimize(seed);

        // 5. Map back to SimulationOverrides
        SimulationOverrides optimizedOverrides = chromosomeMapper.toOverrides(bestSolution);

        Map<Long, List<SimulationOverrides.MortgageEarlyRepaymentOverride>> finalMortgageRepayments = optimizedOverrides
                .mortgageEarlyRepayments();

        Integer payoffYear = bestSolution.getGridSingleMortgageRepaymentYear();
        if (payoffYear != null && baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
            SimulationData finalRunData;
            try {
                finalRunData = baseData.cloneContext();
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException("Failed to clone SimulationData for final optimizer resolution", e);
            }
            assetSimulationEngine.runSimulationWithData(finalRunData, targetAge, optimizedOverrides);
            long actualAmount = finalRunData.getBaseContext().getExecutedEarlyRepaymentPrincipal();

            Long mortgageId = baseData.getAllMortgages().get(0).getId();
            finalMortgageRepayments = Map.of(
                    mortgageId,
                    List.of(new SimulationOverrides.MortgageEarlyRepaymentOverride(payoffYear, actualAmount)));
        }

        SimulationOverrides baseOverrides = command != null ? command.baseOverrides() : null;

        var userProfile = baseData.getBaseContext().getProfile();
        boolean applyProfileToOptimizerBaseline = userProfile != null
                && userProfile.isApplyProfileToOptimizerBaseline();

        Integer appliedRetirementAge = null;
        Integer appliedPensionAge = null;
        Integer appliedIdecoAge = null;
        Long appliedNisaGrowth = null;
        Long appliedNisaTsumitate = null;

        if (applyProfileToOptimizerBaseline) {
            Objects.requireNonNull(userProfile, "User profile is required for applying profile to optimizer baseline");
            if (seed.isRetirementAgeLocked() && userProfile.getTargetRetirementAge() != null) {
                appliedRetirementAge = userProfile.getTargetRetirementAge();
            }
            if (seed.isPensionStartAgeLocked() && userProfile.getPensionStartAge() != null) {
                appliedPensionAge = userProfile.getPensionStartAge();
            }
            if (seed.isIdecoStartAgeLocked() && userProfile.getTaxAdvantagedWithdrawalAge() != null) {
                appliedIdecoAge = userProfile.getTaxAdvantagedWithdrawalAge();
            }
            if (seed.isNisaGrowthTargetLocked() && seed.isNisaTsumitateTargetLocked()) {
                Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository
                        .findByOwnerUserId(userProfile.getId());
                if (nisaSettings.isPresent()) {
                    Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
                    Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
                    if (growthOpt != null && growthOpt > 0) {
                        appliedNisaGrowth = growthOpt;
                    }
                    if (tsumitateOpt != null && tsumitateOpt > 0) {
                        appliedNisaTsumitate = tsumitateOpt;
                    }
                }
            }
        }

        SimulationOverrides.ProfileBaselineUsageDetails appliedProfileBaselineDetails = new SimulationOverrides.ProfileBaselineUsageDetails(
                appliedRetirementAge, appliedPensionAge, appliedIdecoAge, appliedNisaGrowth, appliedNisaTsumitate);

        // Preserve non-optimized fields from baseOverrides (like fund overrides) if
        // necessary
        return new SimulationOverrides(
                optimizedOverrides.testNisaGrowthMonthlyContribution(),
                optimizedOverrides.testNisaTsumitateMonthlyContribution(),
                optimizedOverrides.targetRetirementAgeOverride(),
                optimizedOverrides.pensionStartAgeOverride(),
                optimizedOverrides.idecoStartAgeOverride(),
                finalMortgageRepayments,
                baseOverrides != null ? baseOverrides.fundOverrides() : null,
                baseOverrides != null ? baseOverrides.mortgageRateScenarios() : null,
                appliedProfileBaselineDetails);
    }

    private Chromosome buildSeedChromosome(SimulationData baseData, GlobalOptimizationCommand command) {
        Chromosome seed = new Chromosome();
        OptimizationConfig config = command != null ? command.config() : null;

        var hubConfig = baseData.getBaseContext().getConfig();
        var userProfile = baseData.getBaseContext().getProfile();

        boolean applyProfileToOptimizerBaseline = userProfile != null
                && userProfile.isApplyProfileToOptimizerBaseline();

        // Retirement Age
        seed.setRetirementAgeLocked(config == null || !config.optimizeRetirementAge());

        int currentAge = baseData.getBaseContext().getCurrentAge();

        int initialRetirementAge;
        if (seed.isRetirementAgeLocked() && applyProfileToOptimizerBaseline
                && userProfile != null && userProfile.getTargetRetirementAge() != null) {
            initialRetirementAge = userProfile.getTargetRetirementAge();
        } else {
            initialRetirementAge = hubConfig.getTargetRetirementAge();
        }
        int minRetirementAge = Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge);
        seed.setRetirementAge(Math.max(minRetirementAge, initialRetirementAge));

        // Pension Age
        seed.setPensionStartAgeLocked(config == null || !config.optimizePensionAge());

        int initialPensionAge;
        if (seed.isPensionStartAgeLocked() && applyProfileToOptimizerBaseline
                && userProfile != null && userProfile.getPensionStartAge() != null) {
            initialPensionAge = userProfile.getPensionStartAge();
        } else {
            initialPensionAge = hubConfig.getPensionStartAge();
        }
        int minPensionAge = Math.max(OptimizationConstants.MIN_PENSION_AGE, seed.getRetirementAge());
        seed.setPensionStartAge(Math.max(minPensionAge, initialPensionAge));

        // Ideco Age
        seed.setIdecoStartAgeLocked(config == null || !config.optimizeIdecoAge());

        if (seed.isIdecoStartAgeLocked() && applyProfileToOptimizerBaseline
                && userProfile != null && userProfile.getTaxAdvantagedWithdrawalAge() != null) {
            seed.setIdecoStartAge(userProfile.getTaxAdvantagedWithdrawalAge());
        } else {
            seed.setIdecoStartAge(hubConfig.getIdecoUnlockAge());
        }

        // NISA Target
        boolean nisaLocked = (config == null || !config.optimizeNisa());
        seed.setNisaGrowthTargetLocked(nisaLocked);
        seed.setNisaTsumitateTargetLocked(nisaLocked);

        if (nisaLocked) {
            if (applyProfileToOptimizerBaseline && userProfile != null) {
                Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository
                        .findByOwnerUserId(userProfile.getId());
                if (nisaSettings.isPresent()) {
                    Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
                    Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
                    if (growthOpt != null && growthOpt > 0) {
                        seed.setNisaGrowthTarget(growthOpt);
                    }
                    if (tsumitateOpt != null && tsumitateOpt > 0) {
                        seed.setNisaTsumitateTarget(tsumitateOpt);
                    }
                }
            }
        } else {
            seed.setNisaGrowthTarget(0L);
            seed.setNisaTsumitateTarget(0L);
        }

        // Mortgage Repayments
        if (config != null && config.optimizeMortgage()) {
            seed.setMortgageRepaymentsLocked(false);
            Map<Long, List<MortgageRepaymentGene>> genes = new HashMap<>();
            if (baseData.getAllMortgages() != null) {
                for (MortgageRecord m : baseData.getAllMortgages()) {
                    genes.put(m.getId(), new ArrayList<>());
                }
            }
            seed.setMortgageRepayments(genes);
        } else {
            seed.setMortgageRepaymentsLocked(true);
            seed.setMortgageRepayments(new HashMap<>());
        }

        return seed;
    }

    private long calculateSearchSpace(Chromosome seed, SimulationData baseData, OptimizationStrategy strategy) {
        if (!seed.isMortgageRepaymentsLocked() && strategy != OptimizationStrategy.FORCE_GRID) {
            return Long.MAX_VALUE; // Force GA if mortgage is involved and it's not strictly FORCE_GRID
        }

        long combinations = 1;

        if (!seed.isMortgageRepaymentsLocked()) {
            long mortgageOptions = 1; // default
            if (baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
                var range = OptimizationUtils.calculateMortgageYearRange(baseData.getAllMortgages(),
                        baseData.getBaseContext().getCurrentYear());
                if (range != null) {
                    mortgageOptions = (range.maxYear() - range.minYear() + 1) + 1; // +1 for never repay
                }
            }
            combinations *= mortgageOptions;
        }

        if (!seed.isNisaGrowthTargetLocked() || !seed.isNisaTsumitateTargetLocked()) {
            // Growth: 0 to 200,000 (21 options)
            long growthOptions = OptimizationConstants.MAX_NISA_GROWTH_TARGET
                    / OptimizationConstants.NISA_MONTHLY_TARGET_STEP + 1;
            // Tsumitate: 0 to 100,000 (11 options)
            long tsumitateOptions = OptimizationConstants.MAX_NISA_TSUMITATE_TARGET
                    / OptimizationConstants.NISA_MONTHLY_TARGET_STEP + 1;
            combinations *= (growthOptions * tsumitateOptions);
        }
        if (!seed.isRetirementAgeLocked()) {
            int currentAge = baseData.getBaseContext().getCurrentAge();
            int minRetire = Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge);
            long retirementOptions = Math.max(1, OptimizationConstants.MAX_RETIREMENT_AGE - minRetire + 1);
            combinations *= retirementOptions;
        }
        if (!seed.isPensionStartAgeLocked()) {
            // 60 to 75 (16 options)
            combinations *= (OptimizationConstants.MAX_PENSION_AGE - OptimizationConstants.MIN_PENSION_AGE + 1);
        }
        if (!seed.isIdecoStartAgeLocked()) {
            // 60 to 75 (16 options)
            combinations *= (OptimizationConstants.MAX_IDECO_AGE - OptimizationConstants.MIN_IDECO_AGE + 1);
        }

        return combinations;
    }
}
