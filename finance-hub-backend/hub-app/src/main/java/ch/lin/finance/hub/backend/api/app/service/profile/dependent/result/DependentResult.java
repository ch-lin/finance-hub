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
package ch.lin.finance.hub.backend.api.app.service.profile.dependent.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DependentResult extends BaseResult {
    private Long id;
    private DependentType memberType;
    private LocalDate birthDate;
    private Long estimatedAnnualIncome;
    private Boolean isCohabiting;

    public static DependentResult fromEntity(Dependent entity, String ownerUserId) {
        if (entity == null) {
            return null;
        }
        DependentResult result = new DependentResult();
        result.setId(entity.getId());
        result.setMemberType(entity.getMemberType());
        result.setBirthDate(entity.getBirthDate());
        result.setEstimatedAnnualIncome(entity.getEstimatedAnnualIncome());
        result.setIsCohabiting(entity.getIsCohabiting());
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);
        return result;
    }
}
