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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension;

import java.math.BigDecimal;

import lombok.Value;

/**
 * Immutable DTO representing the active pension configuration rules for
 * simulation.
 */
@Value
public class PensionConfigDto {
    Long maxBasicPensionAmount;
    Long maxBasicPensionMonths;
    BigDecimal kouseiMultiplierPerMille;
    Long kouseiSalaryCapMonthly;
    Long kouseiBonusCapPerPayment;
}
