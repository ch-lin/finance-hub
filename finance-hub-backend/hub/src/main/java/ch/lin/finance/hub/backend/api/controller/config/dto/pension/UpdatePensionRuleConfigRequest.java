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
package ch.lin.finance.hub.backend.api.controller.config.dto.pension;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request DTO for updating an existing PensionRuleConfig.
 */
@Data
public class UpdatePensionRuleConfigRequest {
    private Boolean enabled;

    @Min(value = 0, message = "maxBasicPensionAmount must be positive")
    private Long maxBasicPensionAmount;

    @Min(value = 1, message = "maxBasicPensionMonths must be at least 1")
    private Long maxBasicPensionMonths;

    @DecimalMin(value = "0.001", message = "kouseiMultiplierPerMille must be greater than 0")
    private BigDecimal kouseiMultiplierPerMille;

    @Min(value = 0, message = "kouseiSalaryCapMonthly must be positive")
    private Long kouseiSalaryCapMonthly;

    @Min(value = 0, message = "kouseiBonusCapPerPayment must be positive")
    private Long kouseiBonusCapPerPayment;
}
