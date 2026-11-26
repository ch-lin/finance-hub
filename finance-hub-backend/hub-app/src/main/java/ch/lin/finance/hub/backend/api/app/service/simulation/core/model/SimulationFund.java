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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks the state of an individual fund during the simulation.
 *
 * <p>
 * For NISA funds, {@link #nisaAccountType} carries the sub-quota type
 * ({@link NisaAccountType#GROWTH} or {@link NisaAccountType#TSUMITATE}).
 * For DC/iDeCo funds this field is {@code null}.
 */
@Data
@NoArgsConstructor
public class SimulationFund implements Cloneable {
    private Long fundId;
    private String fundName;
    private long balance;
    private long annualContribution;
    private Double expectedReturnRate;

    /**
     * NISA sub-quota type. {@code null} for non-NISA funds (DC, iDeCo).
     */
    private NisaAccountType nisaAccountType;

    /**
     * Contribution frequency course (e.g. 毎日, 毎週, 毎月).
     */
    private String course;

    /**
     * Raw contribution amount per execution (e.g. 10000 for weekly).
     */
    private long settingAmount;

    /**
     * Explicit constructor setting all essential information fields of an investment fund.
     */
    public SimulationFund(
            Long fundId,
            String fundName,
            long balance,
            long annualContribution,
            Double expectedReturnRate,
            NisaAccountType nisaAccountType,
            String course,
            long settingAmount) {
        this.fundId = fundId;
        this.fundName = fundName;
        this.balance = balance;
        this.annualContribution = annualContribution > 0
                ? annualContribution
                : calculateAnnualContribution(course, settingAmount);
        this.expectedReturnRate = expectedReturnRate;
        this.nisaAccountType = nisaAccountType;
        this.course = course;
        this.settingAmount = settingAmount;
    }

    /**
     * Overloaded constructor that automatically calculates annual contribution from course and setting amount.
     */
    public SimulationFund(
            Long fundId,
            String fundName,
            long balance,
            Double expectedReturnRate,
            NisaAccountType nisaAccountType,
            String course,
            long settingAmount) {
        this(fundId, fundName, balance,
                calculateAnnualContribution(course, settingAmount),
                expectedReturnRate, nisaAccountType, course, settingAmount);
    }

    /**
     * Calculates the intended contribution for the given simulation year.
     * For full years (!isInitialYear), returns {@link #annualContribution}.
     * For the initial transition year, calculates contributions based on remaining cycle.
     */
    public long calculateContribution(boolean isInitialYear, int monthsToAdd) {
        if (!isInitialYear) {
            return annualContribution;
        }

        // If there is no active contribution setting (e.g. balance-only holding), new contribution is 0.
        if (settingAmount <= 0 || course == null) {
            return 0L;
        }

        if (isWeekly()) {
            long weeksToAdd = Math.round(monthsToAdd * 52.0 / 12.0);
            return settingAmount * weeksToAdd;
        }
        if (isDaily()) {
            return settingAmount * (monthsToAdd * 20L);
        }
        if (isMonthly()) {
            return settingAmount * monthsToAdd;
        }

        // Safe fallback for unknown or unsupported frequencies
        return 0L;
    }

    public boolean isWeekly() {
        return isWeekly(this.course);
    }

    public boolean isDaily() {
        return isDaily(this.course);
    }

    public boolean isMonthly() {
        return isMonthly(this.course);
    }

    public static boolean isWeekly(String course) {
        return course != null && ("毎週".equals(course)
                || "weekly".equalsIgnoreCase(course)
                || "every week".equalsIgnoreCase(course));
    }

    public static boolean isDaily(String course) {
        return course != null && ("毎日".equals(course)
                || "daily".equalsIgnoreCase(course)
                || "every day".equalsIgnoreCase(course));
    }

    public static boolean isMonthly(String course) {
        return course != null && ("毎月".equals(course)
                || "monthly".equalsIgnoreCase(course)
                || "every month".equalsIgnoreCase(course));
    }

    /**
     * Calculates the full-year annual contribution based on course and setting amount.
     */
    public static long calculateAnnualContribution(String course, long settingAmount) {
        if (settingAmount <= 0 || course == null) {
            return 0L;
        }
        if (isWeekly(course)) {
            return settingAmount * 52L;
        }
        if (isDaily(course)) {
            return settingAmount * 240L;
        }
        if (isMonthly(course)) {
            return settingAmount * 12L;
        }
        return 0L;
    }

    /**
     * Returns a shallow copy of this fund.
     *
     * <p>
     * {@link CloneNotSupportedException} is declared on {@link Object#clone()} as a
     * checked
     * exception, so the compiler requires it to be handled even though it can never
     * actually be
     * thrown here because {@code SimulationFund} implements {@link Cloneable}. The
     * try-catch is
     * therefore mandatory boilerplate; the {@link AssertionError} rethrow documents
     * that this
     * branch is truly unreachable.
     */
    @Override
    @SuppressWarnings("CloneDeclaresCloneNotSupported")
    public SimulationFund clone() {
        try {
            return (SimulationFund) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Unexpected CloneNotSupportedException", e);
        }
    }
}
