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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

class DefaultConfigFactoryTest {

    private final DefaultConfigFactory factory = new DefaultConfigFactory();

    @Test
    void testCreateSchedulingConfig() {
        FinanceHubDefaultProperties.SourceConfig props = new FinanceHubDefaultProperties.SourceConfig();
        props.setName("test-name");
        props.setEnabled(true);
        props.setAutoStartImportScheduler(true);
        props.setSchedulerType(SchedulerType.CRON);
        props.setFixedRate(1000L);
        props.setCronExpression("0 0 0 * * *");
        props.setCronTimeZone("UTC");

        SchedulingConfig config = factory.create(TransactionSource.AMAZON, props);

        assertEquals("test-name", config.getName());
        assertEquals(TransactionSource.AMAZON, config.getTransactionSource());
        assertTrue(config.getEnabled());
        assertTrue(config.getAutoStartImportScheduler());
        assertEquals(SchedulerType.CRON, config.getSchedulerType());
        assertEquals(1000L, config.getFixedRate());
        assertEquals("0 0 0 * * *", config.getCronExpression());
        assertEquals("UTC", config.getCronTimeZone());
    }

    @Test
    void testCreateFinanceHubConfig() {
        FinanceHubDefaultProperties.SystemConfig props = new FinanceHubDefaultProperties.SystemConfig();
        props.setName("sys-config");
        props.setEnabled(true);
        props.setFileProcessingTimeoutSeconds(60);

        FinanceHubConfig config = factory.create(props);

        assertEquals("sys-config", config.getName());
        assertTrue(config.getEnabled());
        assertEquals(60, config.getFileProcessingTimeoutSeconds());
        assertEquals(12, config.getSimulationEarlyRepaymentMonth());
    }
}
