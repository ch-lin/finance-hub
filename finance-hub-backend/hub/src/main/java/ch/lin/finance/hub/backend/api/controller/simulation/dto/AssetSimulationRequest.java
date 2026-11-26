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

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AssetSimulationRequest(
        Long testNisaGrowthMonthlyContribution,
        Long testNisaTsumitateMonthlyContribution,
        Integer targetRetirementAgeOverride,
        Integer pensionStartAgeOverride,
        Integer idecoStartAgeOverride,
        @Valid Map<Long, List<@Valid MortgageEarlyRepaymentRequest>> mortgageEarlyRepayments,
        @Valid List<FundOverrideRequest> fundOverrides,
        @Valid Map<Long, List<@Valid MortgageRateScenarioRequest>> mortgageRateScenarios) {

    public record FundOverrideRequest(
            String accountType,
            Long fundId,
            Long contributionOverride,
            @DecimalMin(value = "-1.0", message = "Return rate must be between -100% and +100%") @DecimalMax(value = "1.0", message = "Return rate must be between -100% and +100%") Double returnRateOverride) {
    }

    public record MortgageRateScenarioRequest(
            @NotNull(message = "Start year cannot be null") @PositiveOrZero(message = "Start year must be >= 0") Integer startYear,
            @NotNull(message = "Rate cannot be null") @PositiveOrZero(message = "Rate must be >= 0") Double rate) {
    }

    public record MortgageEarlyRepaymentRequest(
            @NotNull(message = "Year cannot be null") @PositiveOrZero(message = "Year must be >= 0") Integer year,
            @NotNull(message = "Amount cannot be null") @Positive(message = "Amount must be positive") Long amount) {
    }
}
