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
package ch.lin.finance.hub.backend.api.app.config.pension;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.config.pension.PensionRuleConfig;

/**
 * A factory component responsible for creating a default
 * {@link PensionRuleConfig} instance from externalized properties.
 */
@Component
public class PensionRuleConfigFactory {

    /**
     * Creates a new {@link PensionRuleConfig} instance from the given properties.
     *
     * @param properties The {@link PensionRuleDefaultProperties} containing default
     *                   values.
     * @return A new, fully populated {@link PensionRuleConfig} object.
     */
    public PensionRuleConfig create(PensionRuleDefaultProperties properties) {
        return new PensionRuleConfig(
                properties.getName(),
                properties.getEnabled(),
                properties.getMaxBasicPensionAmount(),
                properties.getMaxBasicPensionMonths(),
                properties.getKouseiMultiplierPerMille(),
                properties.getKouseiSalaryCapMonthly(),
                properties.getKouseiBonusCapPerPayment());
    }
}
