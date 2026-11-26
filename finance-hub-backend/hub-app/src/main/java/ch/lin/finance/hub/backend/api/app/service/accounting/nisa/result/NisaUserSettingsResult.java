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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NisaUserSettingsResult extends BaseResult {
    private Long monthlyGrowthContribution;
    private Long monthlyTsumitateContribution;
    private Double nisaExpectedReturnRate;

    public static NisaUserSettingsResult fromEntity(NisaUserSettings entity, String ownerUserId) {
        if (entity == null) {
            return null;
        }
        NisaUserSettingsResult result = new NisaUserSettingsResult();
        result.setMonthlyGrowthContribution(entity.getMonthlyGrowthContribution());
        result.setMonthlyTsumitateContribution(entity.getMonthlyTsumitateContribution());
        result.setNisaExpectedReturnRate(entity.getNisaExpectedReturnRate());

        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        return result;
    }
}
