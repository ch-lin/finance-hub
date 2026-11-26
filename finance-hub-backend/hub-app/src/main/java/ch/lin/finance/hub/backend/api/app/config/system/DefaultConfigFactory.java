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
package ch.lin.finance.hub.backend.api.app.config.system;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * A factory component responsible for creating a default
 * {@link SchedulingConfig} instance for a specific transaction source.
 * <p>
 * This factory uses externalized configuration from
 * {@link FinanceHubDefaultProperties.SourceConfig} to construct an
 * {@code SchedulingConfig} domain object. It ensures that a consistent, default
 * configuration is available to the application, for example, on startup or
 * when no other configuration is specified.
 */
@Component
public class DefaultConfigFactory {

    /**
     * Creates a new {@link SchedulingConfig} instance from the given
     * properties.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param properties The {@link FinanceHubDefaultProperties.SourceConfig}
     * containing the values for the default configuration.
     * @return A new, fully populated {@link SchedulingConfig} object.
     */
    public SchedulingConfig create(TransactionSource transactionSource,
            FinanceHubDefaultProperties.SourceConfig properties) {
        SchedulingConfig config = new SchedulingConfig(properties.getName(), transactionSource);
        config.setEnabled(properties.getEnabled());
        config.setAutoStartImportScheduler(properties.getAutoStartImportScheduler());
        config.setSchedulerType(properties.getSchedulerType());
        config.setFixedRate(properties.getFixedRate());
        config.setCronExpression(properties.getCronExpression());
        config.setCronTimeZone(properties.getCronTimeZone());
        return config;
    }

    /**
     * Creates a new {@link FinanceHubConfig} instance from the given global
     * properties, using the provided name.
     *
     * @param configName The custom name for the configuration.
     * @param properties The {@link FinanceHubDefaultProperties.SystemConfig}
     * containing the default values.
     * @return A new, fully populated {@link FinanceHubConfig} object.
     */
    public FinanceHubConfig create(String configName, FinanceHubDefaultProperties.SystemConfig properties) {
        FinanceHubConfig config = new FinanceHubConfig(configName);
        config.setEnabled(properties.getEnabled());
        config.setDefaultCurrency(properties.getDefaultCurrency());
        config.setFileProcessingTimeoutSeconds(properties.getFileProcessingTimeoutSeconds());
        config.setNisaExpectedReturnRate(properties.getNisaExpectedReturnRate());
        config.setIdecoExpectedReturnRate(properties.getIdecoExpectedReturnRate());
        config.setPensionExpectedMonthlySalary(properties.getPensionExpectedMonthlySalary());
        config.setExpenseCalculationPastMonths(properties.getExpenseCalculationPastMonths());
        config.setIdecoUnlockAge(properties.getIdecoUnlockAge());
        config.setDcUnlockAge(properties.getDcUnlockAge());
        config.setTargetRetirementAge(properties.getTargetRetirementAge());
        config.setLifeExpectancy(properties.getLifeExpectancy());
        config.setInflationRate(properties.getInflationRate());
        config.setGaPopulationSize(properties.getGaPopulationSize());
        config.setGaMaxGenerations(properties.getGaMaxGenerations());
        config.setGaMutationRate(properties.getGaMutationRate());
        config.setGaEliteRatio(properties.getGaEliteRatio());
        config.setSimulationEarlyRepaymentMonth(properties.getSimulationEarlyRepaymentMonth());
        config.setRetirementMonthMode(properties.getRetirementMonthMode());

        return config;
    }

    /**
     * Creates a new {@link FinanceHubConfig} instance from the given global
     * properties, using the name defined in the properties.
     *
     * @param properties The {@link FinanceHubDefaultProperties.SystemConfig}
     * containing the default values.
     * @return A new, fully populated {@link FinanceHubConfig} object.
     */
    public FinanceHubConfig create(FinanceHubDefaultProperties.SystemConfig properties) {
        return create(properties.getName(), properties);
    }
}
