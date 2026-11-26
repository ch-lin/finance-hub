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
package ch.lin.finance.hub.backend.api.app.service.job;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;

class JobResultTest {

    @Test
    void testJobResultInstantiation() {
        Map<String, String> failedFiles = Map.of("error.csv", "Format invalid");
        JobResult result = new JobResult(10, 8, 1, 1, failedFiles);

        assertEquals(10, result.totalFiles());
        assertEquals(8, result.successCount());
        assertEquals(1, result.needsReviewCount());
        assertEquals(1, result.failedCount());
        assertEquals(failedFiles, result.failedFiles());
    }

    @Test
    void testJobResult_NullFailedFiles() {
        JobResult result = new JobResult(5, 5, 0, 0, null);

        assertEquals(5, result.totalFiles());
        assertNull(result.failedFiles());
    }
}
