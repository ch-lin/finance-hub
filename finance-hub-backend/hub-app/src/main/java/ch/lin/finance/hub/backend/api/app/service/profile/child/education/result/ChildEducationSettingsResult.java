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
package ch.lin.finance.hub.backend.api.app.service.profile.child.education.result;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChildEducationSettingsResult extends BaseResult {

    private Long id;
    private Long childProfileId;

    private PreschoolTrack preschoolTrack;
    private StageOverrideResult preschoolOverride;
    private Boolean preschoolSplitEnabled;
    private Integer preschoolStage1Duration;
    private PreschoolTrack preschoolTrack2;
    private StageOverrideResult preschoolOverride2;

    private PrimaryTrack primaryTrack;
    private StageOverrideResult primaryOverride;

    private MiddleTrack middleTrack;
    private StageOverrideResult middleOverride;

    private HighTrack highTrack;
    private StageOverrideResult highOverride;

    private UniversityTrack universityTrack;
    private StageOverrideResult universityOverride;
    private BigDecimal manualLivingCost;
    private Boolean isAwayFromHome;

    public static ChildEducationSettingsResult fromEntity(ChildEducationSettings entity, String ownerUserId) {
        if (entity == null) {
            return null;
        }

        ChildEducationSettingsResult result = ChildEducationSettingsResult.builder()
                .id(entity.getId())
                .childProfileId(entity.getChildProfileId())
                .preschoolTrack(entity.getPreschoolTrack())
                .preschoolOverride(StageOverrideResult.fromDomain(entity.getPreschoolOverride()))
                .preschoolSplitEnabled(entity.getPreschoolSplitEnabled())
                .preschoolStage1Duration(entity.getPreschoolStage1Duration())
                .preschoolTrack2(entity.getPreschoolTrack2())
                .preschoolOverride2(StageOverrideResult.fromDomain(entity.getPreschoolOverride2()))
                .primaryTrack(entity.getPrimaryTrack())
                .primaryOverride(StageOverrideResult.fromDomain(entity.getPrimaryOverride()))
                .middleTrack(entity.getMiddleTrack())
                .middleOverride(StageOverrideResult.fromDomain(entity.getMiddleOverride()))
                .highTrack(entity.getHighTrack())
                .highOverride(StageOverrideResult.fromDomain(entity.getHighOverride()))
                .universityTrack(entity.getUniversityTrack())
                .universityOverride(StageOverrideResult.fromDomain(entity.getUniversityOverride()))
                .manualLivingCost(entity.getManualLivingCost())
                .isAwayFromHome(entity.getIsAwayFromHome())
                .build();

        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        return result;
    }
}
