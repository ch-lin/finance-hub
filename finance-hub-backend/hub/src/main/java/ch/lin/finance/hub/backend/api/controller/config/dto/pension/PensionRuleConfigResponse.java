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

import lombok.Data;

/**
 * Response DTO for a PensionRuleConfig.
 */
@Data
public class PensionRuleConfigResponse {
    private String name;
    private Boolean enabled;
    private Long maxBasicPensionAmount;
    private Long maxBasicPensionMonths;
    private BigDecimal kouseiMultiplierPerMille;
    private Long kouseiSalaryCapMonthly;
    private Long kouseiBonusCapPerPayment;
}
