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
package ch.lin.finance.hub.backend.api.app.service.accounting.insurance.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InsurancePolicyResult extends BaseResult {
    private Long id;
    private String companyName;
    private InsurancePolicyType policyType;
    private String policyNumber;
    private String policyName;
    private Long annualPremium;
    private LocalDate endDate;

    public static InsurancePolicyResult fromEntity(InsurancePolicy entity, String ownerUserId) {
        if (entity == null) {
            return null;
        }
        InsurancePolicyResult result = new InsurancePolicyResult();
        result.setId(entity.getId());
        result.setCompanyName(entity.getCompanyName());
        result.setPolicyType(entity.getPolicyType());
        result.setPolicyNumber(entity.getPolicyNumber());
        result.setPolicyName(entity.getPolicyName());
        result.setAnnualPremium(entity.getAnnualPremium());
        result.setEndDate(entity.getEndDate());
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);
        return result;
    }
}
