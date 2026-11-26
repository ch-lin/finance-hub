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
package ch.lin.finance.hub.backend.api.app.service.config.system.command;

import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a command to create a new
 * {@link ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig}.
 * <p>
 * This is an immutable data transfer object (DTO) used to carry necessary
 * information for creating a global configuration.
 */
@Value
@Builder
public class CreateFinanceHubConfigCommand {

    /**
     * The unique name for the new configuration.
     */
    String name;

    /**
     * The enabled status of the new configuration.
     */
    Boolean enabled;

    /**
     * The system-wide default currency.
     */
    CurrencyCode defaultCurrency;

    /**
     * The file processing timeout in seconds.
     */
    Integer fileProcessingTimeoutSeconds;

    /**
     * Expected annual return rate for NISA.
     */
    Double nisaExpectedReturnRate;

    /**
     * Expected annual return rate for iDeCo.
     */
    Double idecoExpectedReturnRate;

    /**
     * Expected average monthly salary for pension projection (in JPY).
     */
    Long pensionExpectedMonthlySalary;

    /**
     * Life expectancy used for calculating lifetime total pension amounts.
     */
    Integer lifeExpectancy;

    /**
     * The age at which iDeCo is unlocked.
     */
    Integer targetRetirementAge;

    Integer pensionStartAge;

    Integer idecoUnlockAge;

    /**
     * The age at which Corporate DC is unlocked.
     */
    Integer dcUnlockAge;

    /**
     * Expected annual inflation rate.
     */
    Double inflationRate;

    Integer gaPopulationSize;
    @Min(value = 1, message = "Elite ratio must be at least 1%")
    @Max(value = 50, message = "Elite ratio cannot exceed 50%")
    Integer gaEliteRatio;
    Integer gaMaxGenerations;
    Double gaMutationRate;
    Integer simulationEarlyRepaymentMonth;
    RetirementMonthMode retirementMonthMode;
}
