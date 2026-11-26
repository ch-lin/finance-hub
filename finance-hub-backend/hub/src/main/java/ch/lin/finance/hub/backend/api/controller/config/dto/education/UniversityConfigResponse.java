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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the university stage configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversityConfigResponse {

    private TrackConfigResponse jpNationalTrack;
    private TrackConfigResponse jpPrivateTrack;
    private TrackConfigResponse jpPrivateArtTrack;
    private TrackConfigResponse twNationalTrack;
    private TrackConfigResponse twPrivateTrack;
    private TrackConfigResponse usTrack;
    private TrackConfigResponse ukTrack;
    private TrackConfigResponse canadaTrack;
    private TrackConfigResponse australiaTrack;
    private BigDecimal jpLivingCost;
    private BigDecimal twLivingCost;
    private BigDecimal usLivingCost;
    private BigDecimal ukLivingCost;
    private BigDecimal canadaLivingCost;
    private BigDecimal australiaLivingCost;
    private BigDecimal livingCost;
}
