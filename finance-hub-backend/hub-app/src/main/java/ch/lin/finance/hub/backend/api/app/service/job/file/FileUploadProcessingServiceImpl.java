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
// TODO: This class is currently unused as the frontend directly sends parsed JSON
// and backend jobs directly read local files. It can be safely removed in the future.
package ch.lin.finance.hub.backend.api.app.service.job.file;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.common.exception.TaskProcessingException;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Service responsible for securely receiving, buffering, and delegating
 * uploaded financial data files.
 * <p>
 * File processing can occasionally be long-running depending on the size of the
 * CSV. This service manages the physical file buffering, sets up a bounded
 * timeout execution context using {@link CompletableFuture}, and guarantees
 * cleanup of temporary resources regardless of success or failure.
 */
@Service
public class FileUploadProcessingServiceImpl implements FileUploadProcessingService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(FileUploadProcessingServiceImpl.class);

    /**
     * Orchestrator to delegate file processing tasks.
     */
    private final FileProcessingOrchestrator fileProcessingOrchestrator;

    /**
     * Service to retrieve global system configuration (e.g., timeout limits).
     */
    private final FinanceHubConfigService financeHubConfigService;

    /**
     * Constructs a new FileUploadProcessingServiceImpl.
     *
     * @param fileProcessingOrchestrator The orchestrator handling the processing
     *                                   logic.
     * @param financeHubConfigService    The service for system configuration.
     */
    public FileUploadProcessingServiceImpl(FileProcessingOrchestrator fileProcessingOrchestrator,
            FinanceHubConfigService financeHubConfigService) {
        this.fileProcessingOrchestrator = fileProcessingOrchestrator;
        this.financeHubConfigService = financeHubConfigService;
    }

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
    @Override
    public FileProcessingResult process(TransactionSource transactionSource, MultipartFile file, boolean dryRun,
            Integer timeoutSecondsOverride) {
        File tempFile = null;
        CompletableFuture<FileProcessingResult> processFuture = null;

        try {
            FinanceHubConfig config = financeHubConfigService.getResolvedConfig(null);
            int timeoutSeconds = 10;
            if (timeoutSecondsOverride != null && timeoutSecondsOverride > 0) {
                timeoutSeconds = timeoutSecondsOverride;
            } else if (config.getFileProcessingTimeoutSeconds() != null) {
                timeoutSeconds = config.getFileProcessingTimeoutSeconds();
            }

            tempFile = File.createTempFile(transactionSource.name().toLowerCase() + "-upload-", ".tmp");
            file.transferTo(Objects.requireNonNull(tempFile));
            final File fileToProcess = tempFile;

            processFuture = CompletableFuture.supplyAsync(
                    () -> fileProcessingOrchestrator.processFile(transactionSource, fileToProcess, dryRun));
            return processFuture.get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            Objects.requireNonNull(processFuture);
            processFuture.cancel(true);
            throw new TaskProcessingException(transactionSource, "TIMEOUT",
                    "Server processing took too long and timed out.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskProcessingException(transactionSource, "INTERRUPTED", "Processing was interrupted.", e);
        } catch (IOException | IllegalStateException | ExecutionException e) {
            throw new TaskProcessingException(transactionSource, "PROCESSING_ERROR",
                    "Failed to read or process file: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    logger.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }
}
