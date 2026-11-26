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
package ch.lin.finance.hub.backend.api.controller.simulation.dto;

import java.util.List;
import java.util.Map;

public record GlobalOptimizerResponse(
        Long testNisaGrowthMonthlyContribution,
        Long testNisaTsumitateMonthlyContribution,
        Integer mortgageEarlyRepaymentYear,
        Integer targetRetirementAgeOverride,
        Integer pensionStartAgeOverride,
        Integer idecoStartAgeOverride,
        Map<Long, List<MortgageEarlyRepaymentResponse>> mortgageEarlyRepayments,
        List<FundOverrideResponse> fundOverrides,
        Map<Long, List<MortgageRateScenarioResponse>> mortgageRateScenarios,
        ProfileBaselineUsageDetailsResponse appliedProfileBaselineDetails) {

    public record ProfileBaselineUsageDetailsResponse(
            Integer targetRetirementAge,
            Integer pensionStartAge,
            Integer idecoStartAge,
            Long nisaGrowthMonthlyContribution,
            Long nisaTsumitateMonthlyContribution) {
    }

    public record FundOverrideResponse(
            String accountType,
            Long fundId,
            Long contributionOverride,
            Double returnRateOverride) {
    }

    public record MortgageRateScenarioResponse(
            Integer startYear,
            Double rate) {
    }

    public record MortgageEarlyRepaymentResponse(
            Integer year,
            Long amount) {
    }
}
