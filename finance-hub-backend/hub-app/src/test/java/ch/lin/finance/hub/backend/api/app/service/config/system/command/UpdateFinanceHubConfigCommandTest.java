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

import org.junit.jupiter.api.Test;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class UpdateFinanceHubConfigCommandTest {

    @Test
    void testBuilder() {
        UpdateFinanceHubConfigCommand command = UpdateFinanceHubConfigCommand.builder()
                .name("test-config")
                .enabled(Optional.of(true))
                .fileProcessingTimeoutSeconds(Optional.of(10))
                .build();
        assertEquals("test-config", command.getName());
        assertTrue(command.getEnabled().get());
        assertEquals(10, command.getFileProcessingTimeoutSeconds().get());
    }
}
