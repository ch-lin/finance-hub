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

import java.util.Optional;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a command to update an existing
 * {@link ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig}.
 * <p>
 * This is an immutable data transfer object (DTO) used to carry update
 * information from an entry point (like a controller) to the service layer.
 * Fields are wrapped in {@link Optional} to support partial updates, allowing
 * clients to specify only the fields they wish to change.
 *
 * @see
 *      ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig
 */
@Value
@Builder
public class UpdateSchedulingConfigCommand {

    /**
     * The unique name of the configuration to update. This field is mandatory
     * for identifying the target entity.
     */
    String name;

    /**
     * An optional new value for the enabled status of the configuration.
     */
    Optional<Boolean> enabled;

    /**
     * An optional new value for auto start import scheduler.
     */
    Optional<Boolean> autoStartImportScheduler;

    /**
     * An optional new scheduler type.
     */
    Optional<SchedulerType> schedulerType;

    /**
     * An optional new fixed rate.
     */
    Optional<Long> fixedRate;

    /**
     * An optional new cron expression.
     */
    Optional<String> cronExpression;

    /**
     * An optional new cron time zone.
     */
    Optional<String> cronTimeZone;
}
