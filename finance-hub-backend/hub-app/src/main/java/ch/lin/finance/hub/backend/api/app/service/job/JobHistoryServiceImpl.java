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

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.job.ImportJobHistoryRepository;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobHistoryResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobFailedFile;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;

/**
 * Standard implementation of {@link JobHistoryService}.
 * <p>
 * Manages transactional boundaries when updating job statuses, ensuring that
 * even if parsing fails, the history of the attempt is accurately recorded.
 */
@Service
public class JobHistoryServiceImpl implements JobHistoryService {

    /**
     * Repository for accessing {@link ImportJobHistory} entities.
     */
    private final ImportJobHistoryRepository importJobHistoryRepository;

    /**
     * Constructs a new JobHistoryServiceImpl.
     *
     * @param importJobHistoryRepository The repository for managing import job
     *                                   history records.
     */
    public JobHistoryServiceImpl(ImportJobHistoryRepository importJobHistoryRepository) {
        this.importJobHistoryRepository = importJobHistoryRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ImportJobHistory startJob(TransactionSource transactionSource, TriggerType triggerType) {
        ImportJobHistory jobHistory = new ImportJobHistory(transactionSource, triggerType, OffsetDateTime.now());
        return importJobHistoryRepository.save(jobHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void completeJob(ImportJobHistory jobHistory, int totalFiles, int successCount, int needsReviewCount,
            int failedCount, Map<String, String> failedFiles) {
        if (jobHistory == null) {
            return; // Tolerate null to gracefully handle dryRun cases
        }

        jobHistory.setTotalFiles(totalFiles);
        jobHistory.setSuccessCount(successCount);
        jobHistory.setNeedsReviewCount(needsReviewCount);
        jobHistory.setFailedCount(failedCount);
        jobHistory.setStatus(failedCount == 0 ? "SUCCESS" : "PARTIAL_SUCCESS");
        jobHistory.setEndTime(OffsetDateTime.now());

        // Automatically map Map<String, String> to ImportJobFailedFile entities
        if (failedFiles != null && !failedFiles.isEmpty()) {
            failedFiles.forEach((filename, errorMessage) -> {
                ImportJobFailedFile failedFile = new ImportJobFailedFile(filename, jobHistory);
                failedFile.setErrorMessage(errorMessage != null ? errorMessage : "Unknown error");
                jobHistory.addFailedFile(failedFile);
            });
        }

        importJobHistoryRepository.save(jobHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void failJob(ImportJobHistory jobHistory, String errorMessage) {
        if (jobHistory == null) {
            return;
        }

        jobHistory.setStatus("FAILED");
        jobHistory.setErrorMessage(errorMessage);
        jobHistory.setEndTime(OffsetDateTime.now());
        importJobHistoryRepository.save(jobHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<JobHistoryResult> getHistoryByTransactionSource(TransactionSource transactionSource,
            Pageable pageable) {
        return importJobHistoryRepository.findAllByTransactionSourceOrderByStartTimeDesc(transactionSource, pageable)
                .map(JobHistoryResult::fromEntity);
    }
}
