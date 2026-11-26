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
package ch.lin.finance.hub.backend.api.controller.config.dto.system;

import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the request body for creating a new
 * {@link ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateFinanceHubConfigRequest {

    /**
     * The unique name for the new configuration. Cannot be blank.
     */
    @NotBlank(message = "Configuration name cannot be blank.")
    private String name;

    /**
     * Indicates whether this configuration should be the active one. If
     * multiple configurations are marked as enabled, the system's behavior may
     * be unpredictable.
     */
    private Boolean enabled;

    /**
     * The system-wide default currency.
     */
    @NotNull(message = "Default currency cannot be null")
    private CurrencyCode defaultCurrency = CurrencyCode.JPY;

    /**
     * The file processing timeout in seconds.
     */
    private Integer fileProcessingTimeoutSeconds;

    /**
     * Expected annual return rate for NISA.
     */
    private Double nisaExpectedReturnRate;

    /**
     * Expected annual return rate for iDeCo.
     */
    @NotNull(message = "iDeCo expected return rate cannot be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "iDeCo expected return rate must be positive")
    private Double idecoExpectedReturnRate;

    /**
     * Expected average monthly salary for pension projection (in JPY).
     */
    @NotNull(message = "Pension expected monthly salary cannot be null")
    @Min(value = 0, message = "Pension expected monthly salary must be positive")
    private Long pensionExpectedMonthlySalary;

    /**
     * Life expectancy used for calculating lifetime total pension amounts.
     */
    @NotNull(message = "Life expectancy cannot be null")
    @Min(value = 0, message = "Life expectancy must be positive")
    private Integer lifeExpectancy = 85;

    /**
     * The target retirement age.
     */
    @NotNull(message = "Target retirement age cannot be null")
    @Min(value = 0, message = "Target retirement age must be positive")
    private Integer targetRetirementAge = 65;

    /**
     * The pension eligible age.
     */
    @NotNull(message = "Pension eligible age cannot be null")
    @Min(value = 60, message = "Pension eligible age must be at least 60")
    @Max(value = 75, message = "Pension eligible age cannot exceed 75")
    private Integer pensionStartAge = 65;

    /**
     * The age at which iDeCo is unlocked.
     */
    @NotNull(message = "iDeCo unlock age cannot be null")
    @Min(value = 60, message = "iDeCo unlock age must be at least 60")
    @Max(value = 75, message = "iDeCo unlock age cannot exceed 75")
    private Integer idecoUnlockAge;

    /**
     * The age at which Corporate DC is unlocked.
     */
    @NotNull(message = "DC unlock age cannot be null")
    @Min(value = 60, message = "DC unlock age must be at least 60")
    @Max(value = 75, message = "DC unlock age cannot exceed 75")
    private Integer dcUnlockAge;

    /**
     * Expected annual inflation rate.
     */
    @NotNull(message = "Inflation rate cannot be null")
    private Double inflationRate;

    private Integer gaPopulationSize;
    private Integer gaMaxGenerations;
    private Double gaMutationRate;
    @Min(value = 1, message = "Elite ratio must be at least 1%")
    @Max(value = 50, message = "Elite ratio cannot exceed 50%")
    private Integer gaEliteRatio;

    @NotNull(message = "Simulation early repayment month cannot be null")
    @Min(value = 1, message = "Simulation early repayment month must be between 1 and 12")
    @Max(value = 12, message = "Simulation early repayment month must be between 1 and 12")
    private Integer simulationEarlyRepaymentMonth = 12;

    @NotNull(message = "Retirement month mode cannot be null")
    private RetirementMonthMode retirementMonthMode = RetirementMonthMode.DECEMBER;
}
