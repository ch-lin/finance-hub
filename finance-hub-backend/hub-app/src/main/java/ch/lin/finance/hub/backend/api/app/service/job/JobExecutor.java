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

import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Defines the execution contract for background data import jobs.
 * <p>
 * Implementations of this interface (e.g., AmazonJobExecutor) encapsulate the
 * specific logic required to fetch, parse, and persist data from their
 * respective
 * integrations.
 */
public interface JobExecutor {

    /**
     * Specifies the data source that this executor is responsible for.
     * <p>
     * Used by the {@link JobOrchestrator} to route execution requests.
     *
     * @return The target {@link TransactionSource}.
     */
    TransactionSource getSource();

    /**
     * Triggers the core processing logic for the import job.
     *
     * @param dryRun If {@code true}, perform all parsing and validation but skip
     *               database persistence.
     * @return A {@link JobResult} summarizing success, failure, and review counts.
     * @throws Exception If an unrecoverable system or networking error occurs
     *                   during execution.
     */
    JobResult execute(boolean dryRun) throws Exception;
}
