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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the request body for partially updating a
 * {@link ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig}.
 */
@Data
@NoArgsConstructor
public class UpdateFinanceHubConfigRequest {

    /**
     * Sets whether this configuration should be the active one.
     */
    private Boolean enabled;

    /**
     * The system-wide default currency.
     */
    private CurrencyCode defaultCurrency;

    /**
     * Updates the file processing timeout in seconds.
     */
    private Integer fileProcessingTimeoutSeconds;

    /**
     * Updates the NISA expected return rate.
     */
    private Double nisaExpectedReturnRate;

    /**
     * Updates the iDeCo expected return rate.
     */
    private Double idecoExpectedReturnRate;

    /**
     * Updates the pension expected monthly salary.
     */
    private Long pensionExpectedMonthlySalary;

    /**
     * Updates the life expectancy.
     */
    private Integer lifeExpectancy;

    /**
     * Updates the target retirement age.
     */
    private Integer targetRetirementAge;

    @Min(value = 60, message = "Pension eligible age must be at least 60")
    @Max(value = 75, message = "Pension eligible age cannot exceed 75")
    private Integer pensionStartAge;

    @Min(value = 60, message = "iDeCo unlock age must be at least 60")
    @Max(value = 75, message = "iDeCo unlock age cannot exceed 75")
    private Integer idecoUnlockAge;

    /**
     * Updates the DC unlock age.
     */
    @Min(value = 60, message = "DC unlock age must be at least 60")
    @Max(value = 75, message = "DC unlock age cannot exceed 75")
    private Integer dcUnlockAge;

    /**
     * Expected annual inflation rate.
     */
    private Double inflationRate;

    private Integer gaPopulationSize;
    private Integer gaMaxGenerations;
    private Double gaMutationRate;
    @Min(value = 1, message = "Elite ratio must be at least 1%")
    @Max(value = 50, message = "Elite ratio cannot exceed 50%")
    private Integer gaEliteRatio;

    @Min(value = 1, message = "Simulation early repayment month must be between 1 and 12")
    @Max(value = 12, message = "Simulation early repayment month must be between 1 and 12")
    private Integer simulationEarlyRepaymentMonth;

    private RetirementMonthMode retirementMonthMode;
}
