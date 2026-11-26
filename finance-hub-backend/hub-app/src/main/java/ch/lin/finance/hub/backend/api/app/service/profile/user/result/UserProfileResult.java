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
package ch.lin.finance.hub.backend.api.app.service.profile.user.result;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.result.InsurancePolicyResult;
import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.app.service.profile.dependent.result.DependentResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserProfileResult extends BaseResult {
    private String id;
    private String displayName;
    private LocalDate birthDate;
    private CurrencyCode defaultCurrency;
    private Long manualMonthlyBaseExpense;
    private Integer targetRetirementAge;
    private Integer pensionStartAge;
    private List<DependentResult> familyMembers;
    private List<InsurancePolicyResult> insurancePolicies;
    private Integer lifeExpectancy;
    private Integer taxAdvantagedWithdrawalAge;
    private boolean applyProfileToOptimizerBaseline;

    public static UserProfileResult fromEntity(UserProfile entity, String ownerUserId, List<InsurancePolicy> policies) {
        if (entity == null) {
            return null;
        }
        UserProfileResult result = new UserProfileResult();
        result.setId(entity.getId());
        result.setDisplayName(entity.getDisplayName());
        result.setBirthDate(entity.getBirthDate());
        result.setDefaultCurrency(entity.getDefaultCurrency());
        result.setManualMonthlyBaseExpense(entity.getManualMonthlyBaseExpense());
        result.setTargetRetirementAge(entity.getTargetRetirementAge());
        result.setPensionStartAge(entity.getPensionStartAge());

        if (entity.getFamilyMembers() != null) {
            result.setFamilyMembers(entity.getFamilyMembers().stream()
                    .map(fm -> DependentResult.fromEntity(fm, ownerUserId))
                    .collect(Collectors.toList()));
        }

        if (policies != null) {
            result.setInsurancePolicies(policies.stream()
                    .map(ip -> InsurancePolicyResult.fromEntity(ip, ownerUserId))
                    .collect(Collectors.toList()));
        }

        result.setLifeExpectancy(entity.getLifeExpectancy());
        result.setTaxAdvantagedWithdrawalAge(entity.getTaxAdvantagedWithdrawalAge());
        result.setApplyProfileToOptimizerBaseline(entity.isApplyProfileToOptimizerBaseline());

        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        return result;
    }
}
