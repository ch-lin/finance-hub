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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Chromosome {

    // Global constraints for random generation
    private static final int MAX_REPAYMENT_COUNT = 5;

    // A flag indicating this gene is locked (will not mutate)
    private boolean retirementAgeLocked;
    private Integer retirementAge;

    private boolean pensionStartAgeLocked;
    /**
     * Age to start receiving public pension (公的年金受給開始年齢). Corresponds to
     * UserProfile.pensionStartAge.
     */
    private Integer pensionStartAge;

    private boolean idecoStartAgeLocked;
    /**
     * Target age to start withdrawing tax-advantaged accounts (iDeCo / DC).
     * Corresponds to UserProfile.taxAdvantagedWithdrawalAge.
     */
    private Integer idecoStartAge;

    private boolean nisaGrowthTargetLocked;
    private Long nisaGrowthTarget;

    private boolean nisaTsumitateTargetLocked;
    private Long nisaTsumitateTarget;

    // 專門給 Grid Search 使用的一維房貸結清年份
    private Integer gridSingleMortgageRepaymentYear;

    // mortgageId -> List of repayment genes
    private boolean mortgageRepaymentsLocked;
    private Map<Long, java.util.List<MortgageRepaymentGene>> mortgageRepayments = new HashMap<>();

    @Data
    @NoArgsConstructor
    public static class MortgageRepaymentGene {

        private Integer year;
        private Long amount;
        private EarlyRepaymentType repaymentType;

        public MortgageRepaymentGene(Integer year, Long amount, EarlyRepaymentType repaymentType) {
            this.year = year;
            this.amount = amount;
            this.repaymentType = repaymentType;
        }

        public MortgageRepaymentGene(Integer year, Long amount) {
            this(year, amount, EarlyRepaymentType.REDUCE_TERM);
        }
    }

    /**
     * Copy Constructor for deep cloning a Chromosome.
     */
    public Chromosome(Chromosome other) {
        this.retirementAgeLocked = other.retirementAgeLocked;
        this.retirementAge = other.retirementAge;

        this.pensionStartAgeLocked = other.pensionStartAgeLocked;
        this.pensionStartAge = other.pensionStartAge;

        this.idecoStartAgeLocked = other.idecoStartAgeLocked;
        this.idecoStartAge = other.idecoStartAge;

        this.nisaGrowthTargetLocked = other.nisaGrowthTargetLocked;
        this.nisaGrowthTarget = other.nisaGrowthTarget;

        this.nisaTsumitateTargetLocked = other.nisaTsumitateTargetLocked;
        this.nisaTsumitateTarget = other.nisaTsumitateTarget;

        this.gridSingleMortgageRepaymentYear = other.gridSingleMortgageRepaymentYear;

        this.mortgageRepaymentsLocked = other.mortgageRepaymentsLocked;

        this.mortgageRepayments = new HashMap<>();
        if (other.mortgageRepayments != null) {
            for (Map.Entry<Long, List<MortgageRepaymentGene>> entry : other.mortgageRepayments.entrySet()) {
                List<MortgageRepaymentGene> genes = new ArrayList<>();
                for (MortgageRepaymentGene g : entry.getValue()) {
                    genes.add(new MortgageRepaymentGene(g.getYear(), g.getAmount(), g.getRepaymentType()));
                }
                this.mortgageRepayments.put(entry.getKey(), genes);
            }
        }
    }

    public void mutate(Random random, int currentYear, int currentAge, int lifeExpectancy) {
        if (!retirementAgeLocked && retirementAge != null) {
            // mutate by +/- 1 to 2 years
            int minRetire = Math.max(OptimizationConstants.MIN_RETIREMENT_AGE, currentAge);
            retirementAge = Math.min(OptimizationConstants.MAX_RETIREMENT_AGE,
                    Math.max(minRetire, retirementAge + (random.nextInt(5) - 2)));
        }
        if (!pensionStartAgeLocked && pensionStartAge != null) {
            int candidateRetire = retirementAge != null ? retirementAge : currentAge;
            int minPension = Math.max(OptimizationConstants.MIN_PENSION_AGE, candidateRetire);
            pensionStartAge = Math.min(OptimizationConstants.MAX_PENSION_AGE,
                    Math.max(minPension, pensionStartAge + (random.nextInt(5) - 2)));
        }
        if (!idecoStartAgeLocked && idecoStartAge != null) {
            idecoStartAge = Math.min(OptimizationConstants.MAX_IDECO_AGE,
                    Math.max(OptimizationConstants.MIN_IDECO_AGE, idecoStartAge + (random.nextInt(5) - 2)));
        }
        if (!nisaGrowthTargetLocked && nisaGrowthTarget != null) {
            long step = (random.nextInt(11) - 5) * 10000L;
            nisaGrowthTarget = Math.min(OptimizationConstants.MAX_NISA_GROWTH_TARGET,
                    Math.max(0L, nisaGrowthTarget + step));
        }
        if (!nisaTsumitateTargetLocked && nisaTsumitateTarget != null) {
            long step = (random.nextInt(11) - 5) * 10000L;
            nisaTsumitateTarget = Math.min(OptimizationConstants.MAX_NISA_TSUMITATE_TARGET,
                    Math.max(0L, nisaTsumitateTarget + step));
        }

        if (!mortgageRepaymentsLocked && mortgageRepayments != null) {
            int minRepayYear = currentYear;

            // Calculate the final calendar year of the user's simulation based on life
            // expectancy
            int maxRepayYearByLifeExpectancy = currentYear - currentAge + lifeExpectancy;

            // Bound the mortgage repayment search space dynamically
            int maxRepayYear = Math.max(minRepayYear + 5, maxRepayYearByLifeExpectancy);

            for (java.util.List<MortgageRepaymentGene> genes : mortgageRepayments.values()) {
                // Add event
                if (genes.size() < MAX_REPAYMENT_COUNT && random.nextDouble() < 0.05) {
                    int randYear = minRepayYear + random.nextInt(maxRepayYear - minRepayYear);
                    long randAmount = 500_000L + random.nextInt(45) * 100_000L; // 500k to 5M
                    EarlyRepaymentType randType = random.nextBoolean()
                            ? EarlyRepaymentType.REDUCE_TERM : EarlyRepaymentType.REDUCE_PAYMENT;
                    genes.add(new MortgageRepaymentGene(randYear, randAmount, randType));
                }

                // Remove event
                if (!genes.isEmpty() && random.nextDouble() < 0.05) {
                    genes.remove(random.nextInt(genes.size()));
                }

                for (MortgageRepaymentGene gene : genes) {
                    if (gene.getYear() != null && gene.getAmount() != null) {
                        // Mutate year by +/- 1 year
                        if (random.nextDouble() < 0.5) {
                            int mutatedYear = gene.getYear() + (random.nextInt(3) - 1);
                            gene.setYear(Math.min(maxRepayYear, Math.max(minRepayYear, mutatedYear)));
                        }
                        // Mutate amount by +/- 10% up to 1,000,000
                        if (random.nextDouble() < 0.5) {
                            long change = (long) ((random.nextDouble() * 2 - 1) * 1000000L);
                            gene.setAmount(Math.max(100000L, gene.getAmount() + change));
                        }
                        // Mutate repayment type with 10% probability
                        if (random.nextDouble() < 0.1) {
                            EarlyRepaymentType currentType = gene.getRepaymentType() != null
                                    ? gene.getRepaymentType()
                                    : EarlyRepaymentType.REDUCE_TERM;
                            gene.setRepaymentType(currentType == EarlyRepaymentType.REDUCE_TERM
                                    ? EarlyRepaymentType.REDUCE_PAYMENT
                                    : EarlyRepaymentType.REDUCE_TERM);
                        }
                    }
                }
            }
        }
    }

    public Chromosome crossover(Chromosome other, Random random) {
        Chromosome child = new Chromosome();

        // Inherit locks from this (assuming both parents have same locks)
        child.setRetirementAgeLocked(this.retirementAgeLocked);
        child.setPensionStartAgeLocked(this.pensionStartAgeLocked);
        child.setIdecoStartAgeLocked(this.idecoStartAgeLocked);
        child.setNisaGrowthTargetLocked(this.nisaGrowthTargetLocked);
        child.setNisaTsumitateTargetLocked(this.nisaTsumitateTargetLocked);
        child.setMortgageRepaymentsLocked(this.mortgageRepaymentsLocked);

        // Uniform crossover for scalar values
        child.setRetirementAge(random.nextBoolean() ? this.retirementAge : other.retirementAge);
        child.setPensionStartAge(random.nextBoolean() ? this.pensionStartAge : other.pensionStartAge);
        if (child.retirementAge != null && child.pensionStartAge != null) {
            int minPension = Math.max(OptimizationConstants.MIN_PENSION_AGE, child.retirementAge);
            if (child.pensionStartAge < minPension) {
                child.setPensionStartAge(minPension);
            }
        }
        child.setIdecoStartAge(random.nextBoolean() ? this.idecoStartAge : other.idecoStartAge);
        child.setNisaGrowthTarget(random.nextBoolean() ? this.nisaGrowthTarget : other.nisaGrowthTarget);
        child.setNisaTsumitateTarget(random.nextBoolean() ? this.nisaTsumitateTarget : other.nisaTsumitateTarget);

        // Inherit mortgage genes
        Map<Long, java.util.List<MortgageRepaymentGene>> childMortgages = new HashMap<>();
        for (Long mortgageId : this.mortgageRepayments.keySet()) {
            java.util.List<MortgageRepaymentGene> genes1 = this.mortgageRepayments.get(mortgageId);
            java.util.List<MortgageRepaymentGene> genes2 = other.mortgageRepayments.get(mortgageId);
            java.util.List<MortgageRepaymentGene> childGenes = new java.util.ArrayList<>();

            int size1 = genes1.size();
            int size2 = (genes2 != null) ? genes2.size() : 0;
            int maxSize = Math.max(size1, size2);

            for (int i = 0; i < maxSize; i++) {
                MortgageRepaymentGene g1 = (i < size1) ? genes1.get(i) : null;
                MortgageRepaymentGene g2 = (genes2 != null && i < size2) ? genes2.get(i) : null;

                MortgageRepaymentGene selected = null;
                if (g1 != null && g2 != null) {
                    selected = random.nextBoolean() ? g1 : g2;
                } else if (g1 != null) {
                    selected = random.nextBoolean() ? g1 : null; // 50% chance to drop if only parent1 has it
                } else if (g2 != null) {
                    selected = random.nextBoolean() ? g2 : null;
                }

                if (selected != null) {
                    childGenes.add(new MortgageRepaymentGene(selected.getYear(), selected.getAmount(), selected.getRepaymentType()));
                }
            }
            childMortgages.put(mortgageId, childGenes);
        }
        child.setMortgageRepayments(childMortgages);

        return child;
    }
}
