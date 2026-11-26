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
package ch.lin.finance.hub.backend.api.app.service.profile.child.education.command;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.StageOverride;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageOverrideCommand {

    private BigDecimal manualTuition;
    private Integer manualDuration;
    private Integer manualStartMonth;
    private Integer manualTermsPerYear;

    public StageOverride toDomain() {
        return StageOverride.builder()
                .manualTuition(manualTuition)
                .manualDuration(manualDuration)
                .manualStartMonth(manualStartMonth)
                .manualTermsPerYear(manualTermsPerYear)
                .build();
    }
}
