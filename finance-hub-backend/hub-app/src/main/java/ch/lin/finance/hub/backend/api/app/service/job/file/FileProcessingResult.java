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
package ch.lin.finance.hub.backend.api.app.service.job.file;

/**
 * Encapsulates the result of processing an uploaded financial data file.
 *
 * @param status Indicates if the process was successful (e.g., "success",
 *               "error")
 * @param dryRun Whether the file was processed in dry-run mode
 * @param data   The resulting parsed entity (e.g., AmazonOrder)
 */
public record FileProcessingResult(
        String status,
        boolean dryRun,
        Object data) {

}
