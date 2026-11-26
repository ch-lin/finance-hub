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
import lombok.Data;

@Data
public class FinanceHubConfigResponse {

    private String name;
    private Boolean enabled;
    private CurrencyCode defaultCurrency;
    private Integer fileProcessingTimeoutSeconds;
    private Double nisaExpectedReturnRate;
    private Double idecoExpectedReturnRate;
    private Long pensionExpectedMonthlySalary;
    private Integer expenseCalculationPastMonths;
    private Integer idecoUnlockAge;
    private Integer dcUnlockAge;
    private Integer targetRetirementAge;
    private Integer pensionStartAge;
    private Integer lifeExpectancy;
    private Double inflationRate;

    private Integer gaPopulationSize;
    private Integer gaMaxGenerations;
    private Double gaMutationRate;
    private Integer gaEliteRatio;
    private Integer simulationEarlyRepaymentMonth;
    private RetirementMonthMode retirementMonthMode;
}
