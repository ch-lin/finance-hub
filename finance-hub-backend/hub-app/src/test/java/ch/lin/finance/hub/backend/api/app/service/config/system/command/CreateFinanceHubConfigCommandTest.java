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
package ch.lin.finance.hub.backend.api.app.service.config.system.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CreateFinanceHubConfigCommandTest {

    @Test
    void testConstructor() {
        CreateFinanceHubConfigCommand command = CreateFinanceHubConfigCommand.builder()
                .name("Test Config")
                .enabled(true)
                .fileProcessingTimeoutSeconds(30)
                .nisaExpectedReturnRate(5.0)
                .idecoExpectedReturnRate(4.0)
                .pensionExpectedMonthlySalary(100000L)
                .idecoUnlockAge(60)
                .dcUnlockAge(60)
                .inflationRate(0.0)
                .build();

        assertEquals("Test Config", command.getName());
        assertTrue(command.getEnabled());
        assertEquals(30, command.getFileProcessingTimeoutSeconds());
        assertEquals(5.0, command.getNisaExpectedReturnRate());
        assertEquals(4.0, command.getIdecoExpectedReturnRate());
        assertEquals(100000L, command.getPensionExpectedMonthlySalary());
        assertEquals(60, command.getIdecoUnlockAge());
        assertEquals(60, command.getDcUnlockAge());
    }
}
