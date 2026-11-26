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
package ch.lin.finance.hub.backend.api.controller.accounting.pension.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionRecordRequest {
    private Long id;

    @NotNull(message = "Record date is required")
    private LocalDate recordDate;

    private Long basicPensionAmount;
    private Long kouseiPensionAmount;
    private Long totalPensionAmount;
    private Integer kokuminCategory1Months;
    private Integer kokuminCategory3Months;
    private Integer kokuminTotalMonths;
    private Integer kouseiGeneralMonths;
    private Integer kouseiTotalMonths;
    private Integer totalEnrolledMonths;
    private Integer qualifyingMonths;
    private Long kokuminPremiumPaid;
    private Long kouseiPremiumPaid;
    private Long totalPremiumPaid;
}
