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
package ch.lin.finance.hub.backend.api.domain.model.config.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class SchedulingConfigTest {

    @Test
    void testConstructorAndDefaultValues() {
        SchedulingConfig config = new SchedulingConfig("amazon-config", TransactionSource.AMAZON);

        assertEquals("amazon-config", config.getName());
        assertEquals(TransactionSource.AMAZON, config.getTransactionSource());
        assertFalse(config.getEnabled());
        assertFalse(config.getAutoStartImportScheduler());
        assertEquals(SchedulerType.FIXED_RATE, config.getSchedulerType());
        assertEquals(3600000L, config.getFixedRate());
        assertNull(config.getCronExpression());
        assertNull(config.getCronTimeZone());
    }
}
