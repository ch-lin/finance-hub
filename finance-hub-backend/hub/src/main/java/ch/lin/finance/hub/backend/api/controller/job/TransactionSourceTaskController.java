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
package ch.lin.finance.hub.backend.api.controller.job;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.app.service.job.JobOrchestrator;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileUploadProcessingService;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;
import ch.lin.platform.api.ApiError;
import ch.lin.platform.api.ApiResponse;

/**
 * REST controller for managing financial data tasks dynamically across
 * different transaction sources.
 */
@RestController
@RequestMapping("/tasks/{transactionSource}")
public class TransactionSourceTaskController {

    /** Logger for this controller. */
    private static final Logger logger = LoggerFactory.getLogger(TransactionSourceTaskController.class);

    /** Orchestrator responsible for managing job executions. */
    private final JobOrchestrator jobOrchestrator;

    /** Service for processing uploaded data files. */
    private final FileUploadProcessingService fileUploadProcessingService;

    /**
     * Constructs a new TransactionSourceTaskController.
     *
     * @param jobOrchestrator             The orchestrator to run and manage jobs.
     * @param fileUploadProcessingService The service for processing file uploads.
     */
    public TransactionSourceTaskController(JobOrchestrator jobOrchestrator,
            FileUploadProcessingService fileUploadProcessingService) {
        this.jobOrchestrator = jobOrchestrator;
        this.fileUploadProcessingService = fileUploadProcessingService;
    }

    /**
     * Initiates asynchronous data processing for a specific transaction source.
     *
     * @param transactionSource The integration source (e.g., AMAZON).
     * @param dryRun            If {@code true}, executes logic without persisting
     *                          data.
     * @return A {@link ResponseEntity} indicating that the process has been
     *         accepted and started in the background.
     */
    @PostMapping(value = "/process", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<?>> processData(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        logger.info("Received request to process data for transaction source: {}, dryRun: {}", transactionSource,
                dryRun);

        if (jobOrchestrator.isJobRunning(transactionSource)) {
            logger.warn("Task is already in progress for transaction source: {}", transactionSource);
            ApiError apiError = new ApiError("TASK_CONFLICT",
                    "Task is already in progress for transaction source: " + transactionSource);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(apiError));
        }

        // Fire-and-forget background execution
        CompletableFuture.runAsync(new DelegatingSecurityContextRunnable(
                () -> jobOrchestrator.executeJob(transactionSource, TriggerType.MANUAL, dryRun)));

        Map<String, Object> result = new HashMap<>();
        result.put("status", "processing");
        result.put("message",
                "Process has been started in the background for transaction source: " + transactionSource);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }

    /**
     * Checks if a processing job is currently running for the specified transaction
     * source.
     *
     * @param transactionSource The integration source to check.
     * @return A {@link ResponseEntity} containing the status of the process.
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessStatus(
            @PathVariable("transactionSource") TransactionSource transactionSource) {
        logger.debug("Checking process status for transaction source: {}", transactionSource);
        Map<String, Object> status = Map.of("isRunning", jobOrchestrator.isJobRunning(transactionSource));
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Uploads and processes a financial data file for a specific transaction
     * source.
     *
     * @param transactionSource      The integration source processing this file.
     * @param file                   The uploaded file payload.
     * @param dryRun                 If {@code true}, validates the file but does
     *                               not persist data.
     * @param timeoutSecondsOverride An optional timeout for the processing
     *                               operation in seconds.
     * @return A {@link ResponseEntity} containing a {@link FileProcessingResult}.
     * @throws Exception If an error occurs during processing.
     */
    // TODO: This upload endpoint is currently unused as the frontend directly sends
    // parsed JSON
    // and backend jobs directly read local files. It can be safely removed in the
    // future.
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadFile(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(value = "timeoutSeconds", required = false) Integer timeoutSecondsOverride) throws Exception {

        FileProcessingResult result = fileUploadProcessingService.process(transactionSource, file, dryRun,
                timeoutSecondsOverride);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
