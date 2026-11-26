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
package ch.lin.finance.hub.backend.api.app.service.config.pension.result;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.domain.model.config.pension.PensionRuleConfig;
import lombok.Builder;
import lombok.Value;

/**
 * Result DTO for a PensionRuleConfig.
 */
@Value
@Builder
public class PensionRuleConfigResult {
    String name;
    Boolean enabled;
    Long maxBasicPensionAmount;
    Long maxBasicPensionMonths;
    BigDecimal kouseiMultiplierPerMille;
    Long kouseiSalaryCapMonthly;
    Long kouseiBonusCapPerPayment;

    public static PensionRuleConfigResult fromEntity(PensionRuleConfig config) {
        if (config == null) {
            return null;
        }
        return PensionRuleConfigResult.builder()
                .name(config.getName())
                .enabled(config.getEnabled())
                .maxBasicPensionAmount(config.getMaxBasicPensionAmount())
                .maxBasicPensionMonths(config.getMaxBasicPensionMonths())
                .kouseiMultiplierPerMille(config.getKouseiMultiplierPerMille())
                .kouseiSalaryCapMonthly(config.getKouseiSalaryCapMonthly())
                .kouseiBonusCapPerPayment(config.getKouseiBonusCapPerPayment())
                .build();
    }
}
