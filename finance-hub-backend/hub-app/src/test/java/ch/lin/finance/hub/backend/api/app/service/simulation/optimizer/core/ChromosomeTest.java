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
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome.MortgageRepaymentGene;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;

class ChromosomeTest {

    @Test
    void testMortgageRepaymentGene_Constructors() {
        MortgageRepaymentGene defaultGene = new MortgageRepaymentGene(2028, 1000000L);
        assertEquals(2028, defaultGene.getYear());
        assertEquals(1000000L, defaultGene.getAmount());
        assertEquals(EarlyRepaymentType.REDUCE_TERM, defaultGene.getRepaymentType());

        MortgageRepaymentGene customGene = new MortgageRepaymentGene(2030, 2000000L, EarlyRepaymentType.REDUCE_PAYMENT);
        assertEquals(2030, customGene.getYear());
        assertEquals(2000000L, customGene.getAmount());
        assertEquals(EarlyRepaymentType.REDUCE_PAYMENT, customGene.getRepaymentType());
    }

    @Test
    void testChromosome_CopyConstructor_PreservesRepaymentType() {
        Chromosome original = new Chromosome();
        List<MortgageRepaymentGene> genes = new ArrayList<>();
        genes.add(new MortgageRepaymentGene(2028, 1500000L, EarlyRepaymentType.REDUCE_PAYMENT));
        genes.add(new MortgageRepaymentGene(2032, 2000000L, EarlyRepaymentType.REDUCE_TERM));
        original.getMortgageRepayments().put(1L, genes);

        Chromosome copy = new Chromosome(original);

        List<MortgageRepaymentGene> copiedGenes = copy.getMortgageRepayments().get(1L);
        assertNotNull(copiedGenes);
        assertEquals(2, copiedGenes.size());
        assertEquals(EarlyRepaymentType.REDUCE_PAYMENT, copiedGenes.get(0).getRepaymentType());
        assertEquals(EarlyRepaymentType.REDUCE_TERM, copiedGenes.get(1).getRepaymentType());
    }

    @Test
    void testChromosome_Crossover_PreservesRepaymentType() {
        Chromosome parent1 = new Chromosome();
        parent1.setMortgageRepayments(Map.of(1L, List.of(
                new MortgageRepaymentGene(2028, 1000000L, EarlyRepaymentType.REDUCE_PAYMENT)
        )));

        Chromosome parent2 = new Chromosome();
        parent2.setMortgageRepayments(Map.of(1L, List.of(
                new MortgageRepaymentGene(2028, 1000000L, EarlyRepaymentType.REDUCE_TERM)
        )));

        Random fixedRandom = new Random(42);
        Chromosome child = parent1.crossover(parent2, fixedRandom);

        List<MortgageRepaymentGene> childGenes = child.getMortgageRepayments().get(1L);
        assertNotNull(childGenes);
        assertEquals(1, childGenes.size());
        assertNotNull(childGenes.get(0).getRepaymentType());
    }

    @Test
    void testMutate_ClampsRetirementAgeToCurrentAge() {
        Chromosome chromosome = new Chromosome();
        chromosome.setRetirementAgeLocked(false);
        chromosome.setRetirementAge(55);

        // When currentAge is 58, mutate should never drop retirementAge below 58
        Random random = new Random(42);
        for (int i = 0; i < 50; i++) {
            chromosome.mutate(random, 2026, 58, 85);
            org.junit.jupiter.api.Assertions.assertTrue(chromosome.getRetirementAge() >= 58,
                    "Retirement age should never be less than currentAge (58), but was: " + chromosome.getRetirementAge());
        }
    }

    @Test
    void testMutate_ClampsPensionAgeToRetirementAgeAndMinPension() {
        Chromosome chromosome = new Chromosome();
        chromosome.setRetirementAgeLocked(true);
        chromosome.setRetirementAge(68);
        chromosome.setPensionStartAgeLocked(false);
        chromosome.setPensionStartAge(68);

        // When retirementAge is 68, mutate should never drop pensionStartAge below 68
        Random random = new Random(42);
        for (int i = 0; i < 50; i++) {
            chromosome.mutate(random, 2026, 40, 85);
            org.junit.jupiter.api.Assertions.assertTrue(chromosome.getPensionStartAge() >= 68,
                    "Pension start age should never be less than retirementAge (68), but was: " + chromosome.getPensionStartAge());
        }
    }

    @Test
    void testCrossover_ClampsPensionAgeToChildRetirementAge() {
        Chromosome parent1 = new Chromosome();
        parent1.setRetirementAge(68);
        parent1.setPensionStartAge(70);

        Chromosome parent2 = new Chromosome();
        parent2.setRetirementAge(55);
        parent2.setPensionStartAge(60);

        Random random = new Random();
        for (int i = 0; i < 50; i++) {
            Chromosome child = parent1.crossover(parent2, random);
            if (child.getRetirementAge() != null && child.getPensionStartAge() != null) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        child.getPensionStartAge() >= Math.max(OptimizationConstants.MIN_PENSION_AGE, child.getRetirementAge()),
                        String.format("Child pension (%d) must be >= max(60, retire (%d))",
                                child.getPensionStartAge(), child.getRetirementAge()));
            }
        }
    }

    @Test
    void testMutate_AllowsMortgageRepaymentInCurrentYear() {
        Chromosome chromosome = new Chromosome();
        chromosome.setMortgageRepaymentsLocked(false);
        List<MortgageRepaymentGene> genes = new ArrayList<>();
        genes.add(new MortgageRepaymentGene(2027, 1000000L));
        chromosome.getMortgageRepayments().put(1L, genes);

        Random random = new Random(42);
        boolean reachedCurrentYear = false;
        for (int i = 0; i < 200; i++) {
            chromosome.mutate(random, 2026, 40, 85);
            List<MortgageRepaymentGene> currentGenes = chromosome.getMortgageRepayments().get(1L);
            if (currentGenes != null) {
                for (MortgageRepaymentGene gene : currentGenes) {
                    org.junit.jupiter.api.Assertions.assertTrue(gene.getYear() >= 2026,
                            "Repayment year should be >= currentYear (2026), but was: " + gene.getYear());
                    if (gene.getYear() == 2026) {
                        reachedCurrentYear = true;
                    }
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(reachedCurrentYear,
                "Mortgage repayment should be allowed to reach currentYear (2026)");
    }
}


