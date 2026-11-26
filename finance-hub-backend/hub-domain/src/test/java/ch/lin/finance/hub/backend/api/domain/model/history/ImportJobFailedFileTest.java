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
package ch.lin.finance.hub.backend.api.domain.model.history;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ImportJobFailedFileTest {

    @Test
    void testConstructorAndGetters() {
        ImportJobHistory history = new ImportJobHistory(
                ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource.AMAZON,
                TriggerType.MANUAL,
                OffsetDateTime.now());
        ImportJobFailedFile failedFile = new ImportJobFailedFile("data.csv", history);
        failedFile.setErrorMessage("Parse error");

        assertEquals("data.csv", failedFile.getFilename());
        assertEquals("Parse error", failedFile.getErrorMessage());
        assertEquals(history, failedFile.getJobHistory());
    }
}
