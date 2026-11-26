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
package ch.lin.finance.hub.backend.api.controller.profile.child.education.dto;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for child education settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildEducationSettingsResponse {

    private Long id;
    private Long childProfileId;
    private String ownerUserId;
    private PrivacyLevel privacyLevel;

    private PreschoolTrack preschoolTrack;
    private StageOverrideResponse preschoolOverride;
    private Boolean preschoolSplitEnabled;
    private Integer preschoolStage1Duration;
    private PreschoolTrack preschoolTrack2;
    private StageOverrideResponse preschoolOverride2;

    private PrimaryTrack primaryTrack;
    private StageOverrideResponse primaryOverride;

    private MiddleTrack middleTrack;
    private StageOverrideResponse middleOverride;

    private HighTrack highTrack;
    private StageOverrideResponse highOverride;

    private UniversityTrack universityTrack;
    private StageOverrideResponse universityOverride;
    private BigDecimal manualLivingCost;
    private Boolean isAwayFromHome;
}
