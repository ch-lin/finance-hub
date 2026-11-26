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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.education;

import java.util.Collections;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Resolved, flattened profile representing the child's academic career across all stages.
 * Pure domain object containing no absolute calendar years or birthdate dependencies.
 */
@Getter
@AllArgsConstructor
@Builder
public class ResolvedEducationProfile {

    @Builder.Default
    private final Map<EducationStage, ResolvedStageData> stages = Collections.emptyMap();

    public ResolvedStageData getStage(EducationStage stage) {
        return stages != null ? stages.get(stage) : null;
    }

    public boolean hasStage(EducationStage stage) {
        ResolvedStageData data = getStage(stage);
        return data != null && data.isActive();
    }
}
