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

import java.time.OffsetDateTime;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;
import lombok.Getter;
import lombok.Setter;

/**
 * Result object for job execution history.
 * Ensures the internal domain entity is not leaked to the controller layer.
 */
@Getter
@Setter
public class JobHistoryResult extends BaseResult {

    private Long id;
    private TransactionSource transactionSource;
    private TriggerType triggerType;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer totalFiles;
    private Integer successCount;
    private Integer failedCount;
    private Integer needsReviewCount;
    private String errorMessage;

    /**
     * Converts a domain entity to a result object.
     *
     * @param entity The domain entity
     * @return The mapped result object
     */
    public static JobHistoryResult fromEntity(ImportJobHistory entity) {
        if (entity == null) {
            return null;
        }
        JobHistoryResult result = new JobHistoryResult();
        result.setId(entity.getId());
        result.setTransactionSource(entity.getTransactionSource());
        result.setTriggerType(entity.getTriggerType());
        result.setStatus(entity.getStatus());
        result.setStartTime(entity.getStartTime());
        result.setEndTime(entity.getEndTime());
        result.setTotalFiles(entity.getTotalFiles());
        result.setSuccessCount(entity.getSuccessCount());
        result.setFailedCount(entity.getFailedCount());
        result.setNeedsReviewCount(entity.getNeedsReviewCount());
        result.setErrorMessage(entity.getErrorMessage());

        // Transfer ownership fields
        result.setOwnerUserId(entity.getOwnerUserId());

        return result;
    }
}
