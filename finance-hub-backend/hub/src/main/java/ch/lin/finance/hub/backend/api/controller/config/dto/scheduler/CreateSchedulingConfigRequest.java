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
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the request body for creating a new
 * {@link
 * ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig}.
 * <p>
 * This DTO is used by the
 * {@link
 * ch.lin.finance.hub.backend.api.controller.config.SchedulingConfigController#createConfig(ch.lin.finance.hub.backend.api.domain.model.config.TransactionSource,
 * CreateSchedulingConfigRequest)}
 * endpoint.
 * <p>
 * Example JSON request body:
 * <pre>
 * {@code
 * {
 * "name": "default",
 * "enabled": false,
 * "autoStartImportScheduler": false
 * }
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSchedulingConfigRequest {

    /**
     * The unique name for the new configuration. Cannot be blank.
     */
    @NotBlank(message = "Configuration name cannot be blank.")
    private String name;

    /**
     * Indicates whether this configuration should be the active one. If
     * multiple configurations are marked as enabled, the system's behavior may
     * be unpredictable.
     */
    private Boolean enabled;

    /**
     * Whether to automatically start the import scheduler.
     */
    private Boolean autoStartImportScheduler;

    /**
     * The type of scheduler to use (e.g., "FIXED_RATE" or "CRON").
     */
    private SchedulerType schedulerType;

    /**
     * The fixed rate for the scheduler in milliseconds.
     */
    private Long fixedRate;

    /**
     * The cron expression for the scheduler.
     */
    private String cronExpression;

    /**
     * The time zone for the cron expression.
     */
    private String cronTimeZone;
}
