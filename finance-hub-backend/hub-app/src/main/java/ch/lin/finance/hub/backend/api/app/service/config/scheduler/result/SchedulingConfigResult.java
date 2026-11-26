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
package ch.lin.finance.hub.backend.api.app.service.config.scheduler.result;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import lombok.Data;

/**
 * Result object for returning scheduling configuration.
 */
@Data
public class SchedulingConfigResult {

    private String name;
    private TransactionSource transactionSource;
    private Boolean enabled;
    private Boolean autoStartImportScheduler;
    private SchedulerType schedulerType;
    private Long fixedRate;
    private String cronExpression;
    private String cronTimeZone;

    /**
     * Converts a SchedulingConfig domain entity to a SchedulingConfigResult.
     *
     * @param entity the SchedulingConfig entity
     * @return the mapped SchedulingConfigResult
     */
    public static SchedulingConfigResult fromEntity(SchedulingConfig entity) {
        if (entity == null) {
            return null;
        }

        SchedulingConfigResult result = new SchedulingConfigResult();

        // Standard properties
        result.setName(entity.getName());
        result.setTransactionSource(entity.getTransactionSource());
        result.setEnabled(entity.getEnabled());
        result.setAutoStartImportScheduler(entity.getAutoStartImportScheduler());
        result.setSchedulerType(entity.getSchedulerType());
        result.setFixedRate(entity.getFixedRate());
        result.setCronExpression(entity.getCronExpression());
        result.setCronTimeZone(entity.getCronTimeZone());

        return result;
    }
}
