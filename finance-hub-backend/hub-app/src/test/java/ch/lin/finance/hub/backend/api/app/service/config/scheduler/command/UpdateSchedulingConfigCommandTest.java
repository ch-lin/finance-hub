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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;

class UpdateSchedulingConfigCommandTest {

    @Test
    void testBuilder() {
        UpdateSchedulingConfigCommand command = UpdateSchedulingConfigCommand.builder()
                .name("test-config")
                .enabled(Optional.of(true))
                .autoStartImportScheduler(Optional.of(true))
                .schedulerType(Optional.of(SchedulerType.CRON))
                .fixedRate(Optional.of(1000L))
                .cronExpression(Optional.of("0 0 * * * *"))
                .cronTimeZone(Optional.of("UTC"))
                .build();
        assertEquals("test-config", command.getName());
        assertTrue(command.getEnabled().get());
        assertTrue(command.getAutoStartImportScheduler().get());
        assertEquals(SchedulerType.CRON, command.getSchedulerType().get());
        assertEquals(1000L, command.getFixedRate().get());
        assertEquals("0 0 * * * *", command.getCronExpression().get());
        assertEquals("UTC", command.getCronTimeZone().get());
    }
}
