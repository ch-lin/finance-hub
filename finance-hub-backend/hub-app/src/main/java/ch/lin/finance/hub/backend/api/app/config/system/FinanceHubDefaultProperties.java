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

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import lombok.Data;

/**
 * A type-safe configuration properties class for the default hub configuration.
 * <p>
 * This class is bound to properties prefixed with {@code finance.hub} in the
 * application's configuration files (e.g., {@code application.yml}). It
 * provides the values used by {@link DefaultConfigFactory} to create a default
 * {@link ch.lin.finance.hub.backend.api.domain.model.config.TransactionSourceConfig}
 * instance.
 * <p>
 * Example in {@code application.yml}:
 *
 * <pre>
 * finance:
 *   hub:
 *     transaction-source-configs:
 *       AMAZON:
 *         name: default-amazon
 *         enabled: true
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "finance.hub")
@Data
public class FinanceHubDefaultProperties {

    /**
     * A map of default source configurations, keyed by their respective
     * {@link TransactionSource}.
     */
    private Map<TransactionSource, SourceConfig> transactionSourceConfigs = new HashMap<>();

    /**
     * Configuration properties related to individual transaction sources (e.g.,
     * AMAZON, RAKUTEN). These properties control scheduling, automation, and
     * default states for each source.
     */
    @Data
    public static class SourceConfig {

        private String name;
        private Boolean enabled = false;
        private Boolean autoStartImportScheduler = false;
        private SchedulerType schedulerType = SchedulerType.CRON;
        private Long fixedRate = 86400000L;
        private String cronExpression = "0 0 9 * * *";
        private String cronTimeZone = "Asia/Taipei";
    }

    /**
     * The default system-wide configuration properties.
     */
    private SystemConfig systemConfig = new SystemConfig();

    /**
     * Core system-wide configuration properties. These dictate global behavior
     * such as file processing timeouts.
     */
    @Data
    public static class SystemConfig {

        private String name = "default";
        private Boolean enabled = false;
        private CurrencyCode defaultCurrency = CurrencyCode.JPY;
        private Integer fileProcessingTimeoutSeconds = 10;
        private Double nisaExpectedReturnRate = 0.05;
        private Double idecoExpectedReturnRate = 0.04;
        private Long pensionExpectedMonthlySalary = 300000L;
        private Integer expenseCalculationPastMonths = 12;
        private Integer idecoUnlockAge = 60;
        private Integer dcUnlockAge = 60;
        private Integer targetRetirementAge = 65;
        private Integer pensionStartAge = 65;
        private Integer lifeExpectancy = 85;
        private Double inflationRate = 0.0;

        // GA Optimizer
        private Integer gaPopulationSize = 50;
        private Integer gaMaxGenerations = 20;
        private Double gaMutationRate = 0.2;
        private Integer gaEliteRatio = 10;

        // Simulation
        private Integer simulationEarlyRepaymentMonth = 12;
        private RetirementMonthMode retirementMonthMode = RetirementMonthMode.DECEMBER;
    }
}
