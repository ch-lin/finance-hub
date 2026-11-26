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

import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating child education settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChildEducationSettingsRequest {

    private PreschoolTrack preschoolTrack;
    @Valid
    private StageOverrideRequest preschoolOverride;
    private Boolean preschoolSplitEnabled;
    private Integer preschoolStage1Duration;
    private PreschoolTrack preschoolTrack2;
    @Valid
    private StageOverrideRequest preschoolOverride2;

    private PrimaryTrack primaryTrack;
    @Valid
    private StageOverrideRequest primaryOverride;

    private MiddleTrack middleTrack;
    @Valid
    private StageOverrideRequest middleOverride;

    private HighTrack highTrack;
    @Valid
    private StageOverrideRequest highOverride;

    private UniversityTrack universityTrack;
    @Valid
    private StageOverrideRequest universityOverride;
    @PositiveOrZero(message = "manualLivingCost must be greater than or equal to 0")
    private BigDecimal manualLivingCost;
    private Boolean isAwayFromHome;
}
