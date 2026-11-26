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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Resolved, flattened parameters for a single education stage after merging
 * global defaults (EducationConfig) with personal overrides
 * (ChildEducationSettings).
 */
@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class ResolvedStageData {

    private final EducationStage stage;
    private final String trackName;
    private final boolean active;

    private final BigDecimal annualTuition;
    private final Integer durationMonths;
    private final Integer startMonth;
    private final Integer termsPerYear;
    private final BigDecimal entranceFee;
    private final BigDecimal monthlyLivingCost;
    private final Integer standardEntranceAge;

    @Builder.Default
    private final List<Integer> paymentMonths = Collections.emptyList();

    /**
     * Optional second stage specifically for Preschool two-stage track transition.
     * Null for all other stages and single-track preschool.
     */
    private final ResolvedStageData stage2;

    /**
     * Checks if this stage has an active second stage (two-stage preschool).
     */
    public boolean hasStage2() {
        return stage2 != null && stage2.isActive();
    }
}
