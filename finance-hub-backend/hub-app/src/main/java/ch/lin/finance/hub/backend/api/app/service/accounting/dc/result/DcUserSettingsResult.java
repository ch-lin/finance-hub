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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DcTargetAllocationSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DcUserSettingsResult extends BaseResult {
    private Long monthlyIdecoContribution;
    private Double idecoExpectedReturnRate;
    private Long monthlyDcMatchingContribution;

    private Boolean isDcContributionYearsOverridden;
    private Integer dcContributionYearsOverride;
    private Boolean isIdecoContributionYearsOverridden;
    private Integer idecoContributionYearsOverride;

    private DcTargetAllocationSetting dcTargetAllocation;
    private DcTargetAllocationSetting idecoTargetAllocation;

    public static DcUserSettingsResult fromEntity(DefinedContributionUserSettings entity, String ownerUserId) {
        if (entity == null) {
            return null;
        }
        DcUserSettingsResult result = new DcUserSettingsResult();
        result.setMonthlyIdecoContribution(entity.getMonthlyIdecoContribution());
        result.setIdecoExpectedReturnRate(entity.getIdecoExpectedReturnRate());
        result.setMonthlyDcMatchingContribution(entity.getMonthlyDcMatchingContribution());

        result.setIsDcContributionYearsOverridden(entity.getIsDcContributionYearsOverridden());
        result.setDcContributionYearsOverride(entity.getDcContributionYearsOverride());
        result.setIsIdecoContributionYearsOverridden(entity.getIsIdecoContributionYearsOverridden());
        result.setIdecoContributionYearsOverride(entity.getIdecoContributionYearsOverride());

        result.setDcTargetAllocation(entity.getDcTargetAllocation());
        result.setIdecoTargetAllocation(entity.getIdecoTargetAllocation());

        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        return result;
    }
}
