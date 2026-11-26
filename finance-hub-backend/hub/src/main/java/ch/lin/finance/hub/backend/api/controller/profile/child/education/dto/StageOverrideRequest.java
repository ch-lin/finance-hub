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
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for overriding default stage parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageOverrideRequest {

    private static final Set<Integer> ALLOWED_TERMS = Set.of(1, 2, 3, 4, 6, 12);

    @PositiveOrZero(message = "manualTuition must be greater than or equal to 0")
    private BigDecimal manualTuition;

    @Min(value = 1, message = "manualDuration must be at least 1 month")
    @Max(value = 240, message = "manualDuration must not exceed 240 months")
    private Integer manualDuration;

    @Min(value = 1, message = "manualStartMonth must be between 1 and 12")
    @Max(value = 12, message = "manualStartMonth must be between 1 and 12")
    private Integer manualStartMonth;

    private Integer manualTermsPerYear;

    @JsonIgnore
    @AssertTrue(message = "manualTermsPerYear must be one of [1, 2, 3, 4, 6, 12]")
    public boolean isManualTermsPerYearValid() {
        return manualTermsPerYear == null || ALLOWED_TERMS.contains(manualTermsPerYear);
    }

    @JsonIgnore
    @AssertTrue(message = "manualDuration must be a multiple of 12 months")
    public boolean isManualDurationMultipleOf12() {
        return manualDuration == null || manualDuration % 12 == 0;
    }
}
