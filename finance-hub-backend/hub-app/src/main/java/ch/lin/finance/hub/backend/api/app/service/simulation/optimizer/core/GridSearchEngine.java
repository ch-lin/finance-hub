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

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GridSearchEngine implements OptimizerEngine {

    private final FitnessEvaluator evaluator;
    private final int currentAge;
    private Integer minMortgageYear = null;
    private Integer maxMortgageYear = null;

    public GridSearchEngine(FitnessEvaluator evaluator, SimulationData baseData) {
        this.evaluator = evaluator;
        this.currentAge = (baseData != null && baseData.getBaseContext() != null)
                ? baseData.getBaseContext().getCurrentAge()
                : OptimizationConstants.MIN_RETIREMENT_AGE;

        if (baseData != null && baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
            var range = OptimizationUtils.calculateMortgageYearRange(baseData.getAllMortgages(),
                    baseData.getBaseContext().getCurrentYear());
            if (range != null) {
                this.minMortgageYear = range.minYear();
                this.maxMortgageYear = range.maxYear();
            }
        }
    }

    @Override
    public Chromosome optimize(Chromosome seed) {
        log.info("Starting Grid Search Engine optimization...");

        // 使用包裝陣列傳遞可變的最佳解狀態
        Chromosome[] bestOverall = { new Chromosome(seed) };
        double[] bestOverallScore = { -Double.MAX_VALUE };
        int[] evaluatedCount = { 0 };

        generateCombinations(seed, new Chromosome(seed), 0, bestOverall, bestOverallScore, evaluatedCount);

        log.info("Finished Grid Search. Evaluated {} combinations. Best overall score: {}", evaluatedCount[0],
                bestOverallScore[0]);
        return bestOverall[0];
    }

    private void generateCombinations(Chromosome seed, Chromosome current, int step, Chromosome[] bestOverall,
            double[] bestOverallScore, int[] evaluatedCount) {
        switch (step) {
            case 0 -> {
                // NISA
                if (!seed.isNisaGrowthTargetLocked() || !seed.isNisaTsumitateTargetLocked()) {
                    for (long growth = 0; growth <= OptimizationConstants.MAX_NISA_GROWTH_TARGET; growth += OptimizationConstants.NISA_MONTHLY_TARGET_STEP) {
                        for (long tsumitate = 0; tsumitate <= OptimizationConstants.MAX_NISA_TSUMITATE_TARGET; tsumitate += OptimizationConstants.NISA_MONTHLY_TARGET_STEP) {
                            current.setNisaGrowthTarget(growth);
                            current.setNisaTsumitateTarget(tsumitate);
                            generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore,
                                    evaluatedCount);
                        }
                    }
                } else {
                    generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                }
            }
            case 1 -> {
                // Retirement Age
                if (!seed.isRetirementAgeLocked()) {
                    int minRetire = Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge);
                    for (int val = minRetire; val <= OptimizationConstants.MAX_RETIREMENT_AGE; val++) {
                        current.setRetirementAge(val);
                        generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                    }
                } else {
                    generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                }
            }
            case 2 -> {
                // Pension Start Age
                if (!seed.isPensionStartAgeLocked()) {
                    int candidateRetire = current.getRetirementAge() != null ? current.getRetirementAge()
                            : OptimizationConstants.MIN_RETIREMENT_AGE;
                    int minPension = Math.max(OptimizationConstants.MIN_PENSION_AGE, candidateRetire);
                    for (int val = minPension; val <= OptimizationConstants.MAX_PENSION_AGE; val++) {
                        current.setPensionStartAge(val);
                        generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                    }
                } else {
                    generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                }
            }
            case 3 -> {
                // Ideco Start Age
                if (!seed.isIdecoStartAgeLocked()) {
                    for (int val = OptimizationConstants.MIN_IDECO_AGE; val <= OptimizationConstants.MAX_IDECO_AGE; val++) {
                        current.setIdecoStartAge(val);
                        generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                    }
                } else {
                    generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                }
            }
            case 4 -> {
                // 1D Mortgage Full Repayment Year
                if (!seed.isMortgageRepaymentsLocked() && minMortgageYear != null && maxMortgageYear != null) {
                    for (int year = minMortgageYear; year <= maxMortgageYear; year++) {
                        current.setGridSingleMortgageRepaymentYear(year);
                        generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                    }
                    // Never repay
                    current.setGridSingleMortgageRepaymentYear(null);
                    generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                } else {
                    generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
                }
            }
            default -> {
                if (++evaluatedCount[0] > OptimizationConstants.MAX_GRID_ENGINE_COMBINATIONS) {
                    throw new IllegalStateException("Grid search engine combinations exceeded hard safety limit: "
                            + OptimizationConstants.MAX_GRID_ENGINE_COMBINATIONS);
                }

                double score = evaluator.evaluate(current);
                if (score > bestOverallScore[0]) {
                    bestOverallScore[0] = score;
                    bestOverall[0] = new Chromosome(current);
                }
            }
        }
    }
}
