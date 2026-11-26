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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeneticAlgorithmEngine implements OptimizerEngine {

    private final FitnessEvaluator evaluator;

    private final int populationSize;
    private final int maxGenerations;
    private final double mutationRate;
    private final int eliteSize;

    private final int currentYear;
    private final int currentAge;
    private final int lifeExpectancy;

    private final Random random = new Random();

    public GeneticAlgorithmEngine(FitnessEvaluator evaluator, SimulationContext baseContext) {
        this.evaluator = evaluator;
        FinanceHubConfig config = baseContext.getConfig();

        if (config.getGaPopulationSize() != null) {
            this.populationSize = config.getGaPopulationSize();
        } else {
            this.populationSize = 50;
        }

        if (config.getGaMaxGenerations() != null) {
            this.maxGenerations = config.getGaMaxGenerations();
        } else {
            this.maxGenerations = 20;
        }

        if (config.getGaMutationRate() != null) {
            this.mutationRate = config.getGaMutationRate();
        } else {
            this.mutationRate = 0.2;
        }

        if (config.getGaEliteRatio() != null) {
            int calculatedEliteSize = Math.max(1, (int) (this.populationSize * (config.getGaEliteRatio() / 100.0)));
            this.eliteSize = Math.min(calculatedEliteSize, this.populationSize);
        } else {
            this.eliteSize = 5;
        }

        this.currentYear = baseContext.getCurrentYear();
        this.currentAge = baseContext.getCurrentAge();

        if (baseContext.getProfile() != null && baseContext.getProfile().getLifeExpectancy() != null) {
            this.lifeExpectancy = baseContext.getProfile().getLifeExpectancy();
        } else if (config.getLifeExpectancy() != null) {
            this.lifeExpectancy = config.getLifeExpectancy();
        } else {
            this.lifeExpectancy = 85;
        }
    }

    @Override
    public Chromosome optimize(Chromosome seed) {
        log.info("Starting Genetic Algorithm optimization...");

        List<Chromosome> population = initializePopulation(seed);
        Chromosome bestOverall = seed;
        double bestOverallScore = evaluator.evaluate(seed);

        for (int generation = 0; generation < maxGenerations; generation++) {
            // Evaluate
            List<ScoredChromosome> scoredPopulation = new ArrayList<>();
            for (Chromosome chromosome : population) {
                double score = evaluator.evaluate(chromosome);
                scoredPopulation.add(new ScoredChromosome(chromosome, score));
            }

            // Sort descending
            scoredPopulation.sort(Comparator.comparingDouble((ScoredChromosome sc) -> sc.score()).reversed());

            // Track best
            ScoredChromosome currentBest = scoredPopulation.get(0);
            if (currentBest.score() > bestOverallScore) {
                bestOverallScore = currentBest.score();
                bestOverall = new Chromosome(currentBest.chromosome());
            }

            log.debug("Generation {} best score: {}", generation, currentBest.score());

            // Next generation
            List<Chromosome> nextGen = new ArrayList<>();

            // Elitism
            for (int i = 0; i < eliteSize; i++) {
                nextGen.add(new Chromosome(scoredPopulation.get(i).chromosome()));
            }

            // Crossover & Mutation
            while (nextGen.size() < populationSize) {
                Chromosome parent1 = selectParent(scoredPopulation);
                Chromosome parent2 = selectParent(scoredPopulation);

                Chromosome child = parent1.crossover(parent2, random);

                if (random.nextDouble() < mutationRate) {
                    child.mutate(random, currentYear, currentAge, lifeExpectancy);
                }

                nextGen.add(child);
            }

            population = nextGen;
        }

        log.info("Finished GA. Best overall score: {}", bestOverallScore);
        return bestOverall;
    }

    private List<Chromosome> initializePopulation(Chromosome seed) {
        List<Chromosome> population = new ArrayList<>();
        // Add the exact seed
        population.add(new Chromosome(seed));

        // Generate mutated variants
        for (int i = 1; i < populationSize; i++) {
            Chromosome variant = new Chromosome(seed);
            // High initial mutation to ensure diversity
            variant.mutate(random, currentYear, currentAge, lifeExpectancy);
            variant.mutate(random, currentYear, currentAge, lifeExpectancy);
            population.add(variant);
        }
        return population;
    }

    private Chromosome selectParent(List<ScoredChromosome> population) {
        // Tournament selection (size 3)
        ScoredChromosome best = population.get(random.nextInt(population.size()));
        for (int i = 0; i < 2; i++) {
            ScoredChromosome contender = population.get(random.nextInt(population.size()));
            if (contender.score() > best.score()) {
                best = contender;
            }
        }
        return new Chromosome(best.chromosome());
    }

    private record ScoredChromosome(Chromosome chromosome, double score) {
    }
}
