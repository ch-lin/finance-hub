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

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for university stage configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversityConfigRequest {

    @Valid
    private TrackConfigRequest jpNationalTrack;
    @Valid
    private TrackConfigRequest jpPrivateTrack;
    @Valid
    private TrackConfigRequest jpPrivateArtTrack;
    @Valid
    private TrackConfigRequest twNationalTrack;
    @Valid
    private TrackConfigRequest twPrivateTrack;
    @Valid
    private TrackConfigRequest usTrack;
    @Valid
    private TrackConfigRequest ukTrack;
    @Valid
    private TrackConfigRequest canadaTrack;
    @Valid
    private TrackConfigRequest australiaTrack;

    @PositiveOrZero(message = "jpLivingCost must be greater than or equal to 0")
    private BigDecimal jpLivingCost;

    @PositiveOrZero(message = "twLivingCost must be greater than or equal to 0")
    private BigDecimal twLivingCost;

    @PositiveOrZero(message = "usLivingCost must be greater than or equal to 0")
    private BigDecimal usLivingCost;

    @PositiveOrZero(message = "ukLivingCost must be greater than or equal to 0")
    private BigDecimal ukLivingCost;

    @PositiveOrZero(message = "canadaLivingCost must be greater than or equal to 0")
    private BigDecimal canadaLivingCost;

    @PositiveOrZero(message = "australiaLivingCost must be greater than or equal to 0")
    private BigDecimal australiaLivingCost;

    @PositiveOrZero(message = "livingCost must be greater than or equal to 0")
    private BigDecimal livingCost;
}
