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
package ch.lin.finance.hub.backend.api.app.service.config.scheduler.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

class CreateSchedulingConfigCommandTest {

    @Test
    void testConstructor() {
        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                "test-config",
                TransactionSource.AMAZON,
                true,
                true,
                SchedulerType.CRON,
                1000L,
                "0 0 * * * *",
                "UTC");

        assertEquals("test-config", command.getName());
        assertEquals(TransactionSource.AMAZON, command.getTransactionSource());
        assertTrue(command.getEnabled());
        assertTrue(command.getAutoStartImportScheduler());
        assertEquals(SchedulerType.CRON, command.getSchedulerType());
        assertEquals(1000L, command.getFixedRate());
        assertEquals("0 0 * * * *", command.getCronExpression());
        assertEquals("UTC", command.getCronTimeZone());
    }
}
