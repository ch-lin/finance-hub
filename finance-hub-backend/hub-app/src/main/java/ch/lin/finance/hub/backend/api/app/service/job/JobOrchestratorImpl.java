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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;

/**
 * Central orchestrator for running data import jobs asynchronously or
 * synchronously.
 * <p>
 * This class coordinates the execution of various {@link JobExecutor}s based on
 * the
 * {@link TransactionSource}. It utilizes concurrent locks
 * ({@link AtomicBoolean}) to ensure
 * that only one job for a given source runs at a time, preventing race
 * conditions or
 * duplicate data ingestion.
 */
@Service
public class JobOrchestratorImpl implements JobOrchestrator {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(JobOrchestratorImpl.class);

    /**
     * Service to manage and persist job execution history.
     */
    private final JobHistoryService historyService;

    /**
     * Map of available job executors, keyed by their supported transaction source.
     */
    private final Map<TransactionSource, JobExecutor> executorMap = new ConcurrentHashMap<>();

    /**
     * Map of concurrent locks to ensure only one job runs per transaction source at
     * a time.
     */
    private final Map<TransactionSource, AtomicBoolean> runLocks = new ConcurrentHashMap<>();

    /**
     * Constructs a new JobOrchestratorImpl.
     *
     * @param historyService The service to record job histories.
     * @param executors      The list of job executors available.
     */
    public JobOrchestratorImpl(JobHistoryService historyService, List<JobExecutor> executors) {
        this.historyService = historyService;
        for (JobExecutor executor : executors) {
            executorMap.put(executor.getSource(), executor);
            runLocks.put(executor.getSource(), new AtomicBoolean(false));
        }
    }

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
    @Override
    public Set<TransactionSource> getSupportedSources() {
        return executorMap.keySet();
    }

    /**
     * Checks if an import job for the specified transaction source is currently
     * running.
     *
     * @param transactionSource The source to check.
     * @return {@code true} if a job is actively processing, {@code false}
     *         otherwise.
     */
    @Override
    public boolean isJobRunning(TransactionSource transactionSource) {
        return runLocks.getOrDefault(transactionSource, new AtomicBoolean(false)).get();
    }

    /**
     * Executes the import job for the specified transaction source.
     * <p>
     * This method attempts to acquire a lock for the source. If acquired, it
     * delegates
     * to the appropriate {@link JobExecutor} and updates the
     * {@link JobHistoryService}
     * with the outcomes (unless {@code dryRun} is active).
     *
     * @param transactionSource The data source to process.
     * @param triggerType       How the job was triggered (e.g., MANUAL, SCHEDULER).
     * @param dryRun            If {@code true}, processes the logic but skips
     *                          persisting history or entities.
     */
    @Override
    public void executeJob(TransactionSource transactionSource, TriggerType triggerType, boolean dryRun) {
        AtomicBoolean lock = runLocks.get(transactionSource);
        if (lock == null) {
            logger.error("No executor found for transaction source: {}", transactionSource);
            return;
        }

        if (!lock.compareAndSet(false, true)) {
            logger.warn("Job for {} is already in progress. Skipping execution.", transactionSource);
            return;
        }

        ImportJobHistory history = null;
        if (!dryRun) {
            history = historyService.startJob(transactionSource, triggerType);
        }

        try {
            JobExecutor executor = executorMap.get(transactionSource);
            JobResult result = executor.execute(dryRun);

            if (!dryRun) {
                historyService.completeJob(history, result.totalFiles(), result.successCount(),
                        result.needsReviewCount(), result.failedCount(), result.failedFiles());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during job execution for transaction source {}", transactionSource, e);
            if (!dryRun) {
                historyService.failJob(history, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        } finally {
            lock.set(false);
        }
    }
}
