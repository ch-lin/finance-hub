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
package ch.lin.finance.hub.backend.api.controller.config.dto.scheduler;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the request body for partially updating a
 * {@link
 * ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig}.
 * <p>
 * This DTO is used by the
 * {@link
 * ch.lin.finance.hub.backend.api.controller.config.SchedulingConfigController#saveConfig(ch.lin.finance.hub.backend.api.domain.model.config.TransactionSource,
 * String, UpdateSchedulingConfigRequest)}
 * endpoint. All fields are optional to allow for partial updates (PATCH).
 * <p>
 * Example JSON request body for a partial update:
 * <pre>
 * {@code
 * {
 * "enabled": true,
 * "autoStartImportScheduler": true
 * }
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateSchedulingConfigRequest {

    /**
     * Sets whether this configuration should be the active one. If multiple
     * configurations are marked as enabled, the system's behavior may be
     * unpredictable.
     */
    private Boolean enabled;

    /**
     * Updates whether to automatically start the import scheduler.
     */
    private Boolean autoStartImportScheduler;

    /**
     * Updates the type of scheduler to use.
     */
    private SchedulerType schedulerType;

    /**
     * Updates the fixed rate for the scheduler in milliseconds.
     */
    private Long fixedRate;

    /**
     * Updates the cron expression for the scheduler.
     */
    private String cronExpression;

    /**
     * Updates the time zone for the cron expression.
     */
    private String cronTimeZone;
}
