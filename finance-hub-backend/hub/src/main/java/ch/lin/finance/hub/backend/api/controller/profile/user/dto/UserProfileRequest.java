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
package ch.lin.finance.hub.backend.api.controller.profile.user.dto;

import java.time.LocalDate;
import java.util.List;

import ch.lin.finance.hub.backend.api.controller.accounting.insurance.dto.InsurancePolicyRequest;
import ch.lin.finance.hub.backend.api.controller.profile.dependent.dto.DependentRequest;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UserProfileRequest(
        String id,
        String displayName,
        LocalDate birthDate,
        CurrencyCode defaultCurrency,
        Long manualMonthlyBaseExpense,
        Integer targetRetirementAge,
        @Min(60) @Max(75) Integer pensionStartAge,
        @Valid List<DependentRequest> familyMembers,
        List<InsurancePolicyRequest> insurancePolicies,
        Integer lifeExpectancy,
        @Min(60) @Max(75) Integer taxAdvantagedWithdrawalAge,
        Boolean applyProfileToOptimizerBaseline) {
}
