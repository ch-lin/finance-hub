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
package ch.lin.finance.hub.backend.api.app.service.simulation.command;

import java.util.List;
import java.util.Map;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;

/**
 * Holds parameters used by external Solvers to override the baseline simulation
 * behavior. This allows the pure simulation engine to test different "parallel
 * universe" scenarios.
 */
public record SimulationOverrides(
        Long testNisaGrowthMonthlyContribution,
        Long testNisaTsumitateMonthlyContribution,
        Integer targetRetirementAgeOverride,
        /**
         * Overrides the public pension start age (公的年金受給開始年齢). Corresponds to
         * UserProfile.pensionStartAge.
         */
        Integer pensionStartAgeOverride,
        /**
         * Overrides the target age to start withdrawing tax-advantaged accounts
         * (iDeCo / DC). Corresponds to UserProfile.taxAdvantagedWithdrawalAge
         * (not contribution start age).
         */
        Integer idecoStartAgeOverride,
        Map<Long, List<MortgageEarlyRepaymentOverride>> mortgageEarlyRepayments,
        List<FundOverride> fundOverrides,
        Map<Long, List<MortgageRateScenarioOverride>> mortgageRateScenarios,
        ProfileBaselineUsageDetails appliedProfileBaselineDetails) {

    public record ProfileBaselineUsageDetails(
            Integer targetRetirementAge,
            /**
             * Baseline public pension start age applied from UserProfile.
             * Corresponds to UserProfile.pensionStartAge.
             */
            Integer pensionStartAge,
            /**
             * Baseline tax-advantaged withdrawal age applied from UserProfile.
             * Corresponds to UserProfile.taxAdvantagedWithdrawalAge.
             */
            Integer idecoStartAge,
            Long nisaGrowthMonthlyContribution,
            Long nisaTsumitateMonthlyContribution) {

        public boolean anyUsed() {
            return targetRetirementAge != null || pensionStartAge != null || idecoStartAge != null
                    || nisaGrowthMonthlyContribution != null
                    || nisaTsumitateMonthlyContribution != null;
        }
    }

    public record FundOverride(
            String accountType, // "NISA", "IDECO", "CORPORATE_DC"
            Long fundId,
            Long contributionOverride,
            Double returnRateOverride) {

    }

    public record MortgageRateScenarioOverride(
            Integer startYear,
            Double rate) {

    }

    public record MortgageEarlyRepaymentOverride(
            Integer year,
            Long amount,
            EarlyRepaymentType repaymentType) {

        public MortgageEarlyRepaymentOverride(Integer year, Long amount) {
            this(year, amount, EarlyRepaymentType.REDUCE_TERM);
        }
    }
}
