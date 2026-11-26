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
package ch.lin.finance.hub.backend.api.common.exception;

import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

import static org.junit.jupiter.api.Assertions.*;

class TaskProcessingExceptionTest {
    @Test
    void testConstructorAndGetters() {
        Throwable cause = new RuntimeException("cause");
        TaskProcessingException ex = new TaskProcessingException(
                TransactionSource.AMAZON,
                "ERR_01",
                "Task failed",
                cause);

        assertEquals("Task failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals(TransactionSource.AMAZON, ex.getTransactionSource());
        assertEquals("ERR_01", ex.getErrorCode());
    }
}
