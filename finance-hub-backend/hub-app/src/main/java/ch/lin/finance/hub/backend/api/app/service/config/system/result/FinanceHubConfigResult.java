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
package ch.lin.finance.hub.backend.api.app.service.config.system.result;

import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import lombok.Data;

/**
 * Result object for returning finance hub global configuration.
 */
@Data
public class FinanceHubConfigResult {

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

    /**
     * Converts a FinanceHubConfig domain entity to a FinanceHubConfigResult.
     *
     * @param entity the FinanceHubConfig entity
     * @return the mapped FinanceHubConfigResult
     */
    public static FinanceHubConfigResult fromEntity(FinanceHubConfig entity) {
        if (entity == null) {
            return null;
        }

        FinanceHubConfigResult result = new FinanceHubConfigResult();

        // Standard properties
        result.setName(entity.getName());
        result.setEnabled(entity.getEnabled());
        result.setDefaultCurrency(entity.getDefaultCurrency());
        result.setFileProcessingTimeoutSeconds(entity.getFileProcessingTimeoutSeconds());
        result.setNisaExpectedReturnRate(entity.getNisaExpectedReturnRate());
        result.setIdecoExpectedReturnRate(entity.getIdecoExpectedReturnRate());
        result.setPensionExpectedMonthlySalary(entity.getPensionExpectedMonthlySalary());
        result.setExpenseCalculationPastMonths(entity.getExpenseCalculationPastMonths());
        result.setIdecoUnlockAge(entity.getIdecoUnlockAge());
        result.setDcUnlockAge(entity.getDcUnlockAge());
        result.setTargetRetirementAge(entity.getTargetRetirementAge());
        result.setPensionStartAge(entity.getPensionStartAge());
        result.setLifeExpectancy(entity.getLifeExpectancy());
        result.setInflationRate(entity.getInflationRate());

        result.setGaPopulationSize(entity.getGaPopulationSize());
        result.setGaMaxGenerations(entity.getGaMaxGenerations());
        result.setGaMutationRate(entity.getGaMutationRate());
        result.setGaEliteRatio(entity.getGaEliteRatio());
        result.setSimulationEarlyRepaymentMonth(entity.getSimulationEarlyRepaymentMonth());
        result.setRetirementMonthMode(entity.getRetirementMonthMode());

        return result;
    }
}
