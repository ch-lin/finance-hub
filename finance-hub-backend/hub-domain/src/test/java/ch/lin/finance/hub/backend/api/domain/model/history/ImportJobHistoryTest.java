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

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ImportJobHistoryTest {

    @Test
    void testConstructorAndDefaultValues() {
        OffsetDateTime startTime = OffsetDateTime.now();
        ImportJobHistory history = new ImportJobHistory(TransactionSource.AMAZON, TriggerType.MANUAL, startTime);

        assertEquals(TransactionSource.AMAZON, history.getTransactionSource());
        assertEquals(TriggerType.MANUAL, history.getTriggerType());
        assertEquals(startTime, history.getStartTime());

        assertEquals("PROCESSING", history.getStatus());
        assertEquals(0, history.getTotalFiles());
        assertEquals(0, history.getSuccessCount());
        assertEquals(0, history.getFailedCount());
        assertEquals(0, history.getNeedsReviewCount());
        assertTrue(history.getFailedFiles().isEmpty());
    }

    @Test
    void testAddFailedFile() {
        ImportJobHistory history = new ImportJobHistory(TransactionSource.RAKUTEN, TriggerType.SYSTEM,
                OffsetDateTime.now());
        ImportJobFailedFile failedFile = new ImportJobFailedFile("file.csv", history);

        history.addFailedFile(failedFile);

        assertEquals(1, history.getFailedFiles().size());
        assertTrue(history.getFailedFiles().contains(failedFile));
        assertEquals(history, failedFile.getJobHistory());
    }
}
