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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

class FinanceHubDefaultPropertiesTest {

    @Test
    void testSourceConfigDefaults() {
        FinanceHubDefaultProperties.SourceConfig config = new FinanceHubDefaultProperties.SourceConfig();
        assertNull(config.getName());
        assertFalse(config.getEnabled());
        assertFalse(config.getAutoStartImportScheduler());
        assertEquals(SchedulerType.CRON, config.getSchedulerType());
        assertEquals(86400000L, config.getFixedRate());
        assertEquals("0 0 9 * * *", config.getCronExpression());
        assertEquals("Asia/Taipei", config.getCronTimeZone());
    }

    @Test
    void testSystemConfigDefaults() {
        FinanceHubDefaultProperties.SystemConfig config = new FinanceHubDefaultProperties.SystemConfig();
        assertEquals("default", config.getName());
        assertFalse(config.getEnabled());
        assertEquals(10, config.getFileProcessingTimeoutSeconds());
        assertEquals(12, config.getSimulationEarlyRepaymentMonth());
    }

    @Test
    void testPropertiesGettersAndSetters() {
        FinanceHubDefaultProperties props = new FinanceHubDefaultProperties();
        FinanceHubDefaultProperties.SourceConfig sourceConfig = new FinanceHubDefaultProperties.SourceConfig();
        props.setTransactionSourceConfigs(Map.of(TransactionSource.AMAZON, sourceConfig));

        FinanceHubDefaultProperties.SystemConfig sysConfig = new FinanceHubDefaultProperties.SystemConfig();
        props.setSystemConfig(sysConfig);

        assertEquals(1, props.getTransactionSourceConfigs().size());
        assertEquals(sourceConfig, props.getTransactionSourceConfigs().get(TransactionSource.AMAZON));
        assertEquals(sysConfig, props.getSystemConfig());
    }
}
