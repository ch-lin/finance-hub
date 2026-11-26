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
package ch.lin.finance.hub.backend.api.app.service.config.scheduler.command;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import lombok.Value;

/**
 * Represents a command to create a new
 * {@link ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig}.
 * <p>
 * This is an immutable data transfer object (DTO) used to carry all necessary
 * information from an entry point (like a controller) to the service layer for
 * creating a configuration. All fields are expected to be non-null.
 *
 * @see
 *      ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig
 */
@Value
public class CreateSchedulingConfigCommand {

    /**
     * The unique name for the new configuration.
     */
    String name;

    /**
     * The transaction source type for the new configuration.
     */
    TransactionSource transactionSource;

    /**
     * The enabled status of the new configuration.
     */
    Boolean enabled;

    /**
     * Whether to automatically start the import scheduler.
     */
    Boolean autoStartImportScheduler;

    /**
     * The type of scheduler to use.
     */
    SchedulerType schedulerType;

    /**
     * The fixed rate for the scheduler in milliseconds.
     */
    Long fixedRate;

    /**
     * The cron expression for the scheduler.
     */
    String cronExpression;

    /**
     * The time zone for the cron expression.
     */
    String cronTimeZone;
}
