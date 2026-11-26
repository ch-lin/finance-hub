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

import java.util.Set;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;

/**
 * Central orchestrator for running data import jobs asynchronously or
 * synchronously.
 */
public interface JobOrchestrator {

    /**
     * Returns the set of integration sources that currently have a registered
     * executor.
     * <p>
     * This dynamically ties the configuration/scheduler to the
     * available strategies, ensuring the UI or scheduler only shows options
     * that the backend can actually execute.
     *
     * @return A set of supported {@link TransactionSource}s.
     */
    Set<TransactionSource> getSupportedSources();

    /**
     * Checks if an import job for the specified transaction source is currently
     * running.
     *
     * @param transactionSource The source to check.
     * @return {@code true} if a job is actively processing, {@code false}
     *         otherwise.
     */
    boolean isJobRunning(TransactionSource transactionSource);

    /**
     * Executes the import job for the specified transaction source.
     *
     * @param transactionSource The data source to process.
     * @param triggerType       How the job was triggered (e.g., MANUAL, SCHEDULER).
     * @param dryRun            If {@code true}, processes the logic but skips
     *                          persisting history or entities.
     */
    void executeJob(TransactionSource transactionSource, TriggerType triggerType, boolean dryRun);
}
