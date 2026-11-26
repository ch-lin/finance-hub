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

import ch.lin.finance.hub.backend.api.controller.accounting.insurance.dto.InsurancePolicyResponse;
import ch.lin.finance.hub.backend.api.controller.profile.dependent.dto.DependentResponse;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;

public record UserProfileResponse(
        String id,
        String displayName,
        LocalDate birthDate,
        CurrencyCode defaultCurrency,
        Long manualMonthlyBaseExpense,
        Integer targetRetirementAge,
        Integer pensionStartAge,
        List<DependentResponse> familyMembers,
        List<InsurancePolicyResponse> insurancePolicies,
        Integer lifeExpectancy,
        Integer taxAdvantagedWithdrawalAge,
        Boolean applyProfileToOptimizerBaseline) {
}
