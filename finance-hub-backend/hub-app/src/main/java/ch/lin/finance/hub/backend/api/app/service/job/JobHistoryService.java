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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ch.lin.finance.hub.backend.api.app.service.job.result.JobHistoryResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;

/**
 * Interface for managing job execution history.
 * <p>
 * This service handles the lifecycle of an {@link ImportJobHistory} entity,
 * tracking
 * when automated or manual import jobs start, succeed, fail, or finish
 * partially.
 */
public interface JobHistoryService {

    /**
     * Initializes and persists a new job history record, marking its start time.
     *
     * @param transactionSource The data source being imported (e.g., AMAZON).
     * @param triggerType       Whether the job was triggered manually, by
     *                          scheduler, or API.
     * @return The freshly created and persisted {@link ImportJobHistory} entity.
     */
    ImportJobHistory startJob(TransactionSource transactionSource, TriggerType triggerType);

    /**
     * Completes a job history record, saving statistics and mapping any failed
     * files.
     *
     * @param jobHistory       The history record to complete (nullable, e.g.,
     *                         during
     *                         a dry run)
     * @param totalFiles       Total number of files processed
     * @param successCount     Number of successfully processed files
     * @param needsReviewCount Number of files needing review
     * @param failedCount      Number of files that failed completely
     * @param failedFiles      Map of filename to error message for failed files
     */
    void completeJob(ImportJobHistory jobHistory, int totalFiles, int successCount, int needsReviewCount,
            int failedCount, Map<String, String> failedFiles);

    /**
     * Marks a job history record as failed due to a system or generic error.
     *
     * @param jobHistory   The history record to fail (nullable)
     * @param errorMessage The reason for the failure
     */
    void failJob(ImportJobHistory jobHistory, String errorMessage);

    /**
     * Retrieves a paginated list of job histories for a specific transaction
     * source,
     * ordered from newest to oldest.
     *
     * @param transactionSource The source to filter job histories by.
     * @param pageable          Pagination settings.
     * @return A page of relevant
     *         {@link ch.lin.finance.hub.backend.api.app.service.job.result.JobHistoryResult}
     *         records.
     */
    Page<JobHistoryResult> getHistoryByTransactionSource(TransactionSource transactionSource, Pageable pageable);
}
