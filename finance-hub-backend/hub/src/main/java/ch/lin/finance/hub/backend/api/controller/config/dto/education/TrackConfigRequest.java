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
package ch.lin.finance.hub.backend.api.controller.config.dto.education;

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
 * Request DTO for configuring an education track.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackConfigRequest {

    private static final Set<Integer> ALLOWED_TERMS = Set.of(1, 2, 3, 4, 6, 12);

    @PositiveOrZero(message = "tuition must be greater than or equal to 0")
    private BigDecimal tuition;

    @Min(value = 1, message = "duration must be at least 1 month")
    @Max(value = 240, message = "duration must not exceed 240 months")
    private Integer duration;

    @Min(value = 1, message = "startMonth must be between 1 and 12")
    @Max(value = 12, message = "startMonth must be between 1 and 12")
    private Integer startMonth;

    private Integer termsPerYear;

    @PositiveOrZero(message = "entranceFee must be greater than or equal to 0")
    private BigDecimal entranceFee;

    @Min(value = 0, message = "standardEntranceAge must be greater than or equal to 0")
    @Max(value = 30, message = "standardEntranceAge must not exceed 30")
    private Integer standardEntranceAge;

    @JsonIgnore
    @AssertTrue(message = "termsPerYear must be one of [1, 2, 3, 4, 6, 12]")
    public boolean isTermsPerYearValid() {
        return termsPerYear == null || ALLOWED_TERMS.contains(termsPerYear);
    }
}
