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
// TODO: This interface and its implementations are currently unused as the frontend directly sends parsed JSON
// and backend jobs directly read local files. It can be safely removed in the future.
package ch.lin.finance.hub.backend.api.app.service.job.file;

import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.common.exception.TaskProcessingException;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Service responsible for securely receiving, buffering, and delegating
 * uploaded financial data files.
 */
public interface FileUploadProcessingService {

    /**
     * Processes an uploaded multipart file for a specific transaction source.
     * <p>
     * The file is temporarily staged on disk, then processed asynchronously with
     * a strictly enforced timeout to prevent thread starvation. If the timeout is
     * breached, the operation is canceled and an exception is raised.
     *
     * @param transactionSource      The integration source (e.g., AMAZON).
     * @param file                   The uploaded file payload from the HTTP
     *                               request.
     * @param dryRun                 If {@code true}, validates the file but rolls
     *                               back any DB persistence.
     * @param timeoutSecondsOverride Optional timeout override in seconds; if null,
     *                               falls back to global config.
     * @return A {@link FileProcessingResult} containing parsing statistics and
     *         parsed entities.
     * @throws TaskProcessingException If the file cannot be read, processing fails,
     *                                 or processing times out.
     */
    FileProcessingResult process(TransactionSource transactionSource, MultipartFile file, boolean dryRun,
            Integer timeoutSecondsOverride);
}
