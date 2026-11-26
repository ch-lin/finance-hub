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
package ch.lin.finance.hub.backend.api.app.service.job.result;

import java.util.Map;

/**
 * Standardized result object returned by all Integration Source executors.
 *
 * @param totalFiles       Total number of files processed
 * @param successCount     Number of successfully processed files
 * @param needsReviewCount Number of files needing review
 * @param failedCount      Number of files that failed completely
 * @param failedFiles      Map of filename to error message for failed files
 */
public record JobResult(
        int totalFiles,
        int successCount,
        int needsReviewCount,
        int failedCount,
        Map<String, String> failedFiles) {

}
