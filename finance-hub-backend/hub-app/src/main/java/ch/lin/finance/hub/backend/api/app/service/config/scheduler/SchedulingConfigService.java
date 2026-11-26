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
package ch.lin.finance.hub.backend.api.app.service.config.scheduler;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.CreateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.UpdateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.result.SchedulingConfigResult;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.result.TimeZoneOption;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Defines the service layer contract for managing transaction source
 * configurations.
 * <p>
 * This interface outlines the core business logic for creating, retrieving,
 * updating, and deleting {@link SchedulingConfig} entities. It also
 * provides a mechanism for resolving the active configuration to be used by the
 * application.
 */
public interface SchedulingConfigService {

    /**
     * Retrieves a summary of all available configurations for a specific
     * transaction source.
     *
     * @param transactionSource The transaction source to filter by.
     * @return An {@link AllConfigsData} object containing the name of the
     *         currently enabled configuration and a list of all configuration
     *         names.
     * @throws ch.lin.platform.exception.ConfigCreationException if the
     *                                                           'default'
     *                                                           configuration needs
     *                                                           to be created but
     *                                                           fails.
     */
    AllConfigsData getAllConfigs(TransactionSource transactionSource);

    /**
     * Creates and persists a new hub configuration.
     *
     * @param command A {@link CreateSchedulingConfigCommand} containing the data
     *                for the
     *                new configuration.
     * @return The newly created {@link SchedulingConfig} entity.
     * @throws ch.lin.platform.exception.InvalidRequestException if the
     *                                                           configuration name
     *                                                           is 'default' or
     *                                                           already exists.
     */
    SchedulingConfig createConfig(CreateSchedulingConfigCommand command);

    /**
     * Deletes all hub configurations from the database. This is a destructive
     * operation and should be used with caution.
     */
    void deleteAllConfigs();

    /**
     * Deletes a specific hub configuration by its name.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param name              The unique name of the configuration to delete.
     * @throws ch.lin.platform.exception.InvalidRequestException if attempting
     *                                                           to delete the
     *                                                           system-reserved
     *                                                           default
     *                                                           configuration.
     * @throws ch.lin.platform.exception.ConfigNotFoundException if no
     *                                                           configuration with
     *                                                           the given name is
     *                                                           found.
     */
    void deleteConfig(TransactionSource transactionSource, String name);

    /**
     * Retrieves a specific hub configuration by its name.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param name              The unique name of the configuration to retrieve.
     * @return The corresponding {@link SchedulingConfig} entity.
     * @throws ch.lin.platform.exception.ConfigNotFoundException if no
     *                                                           configuration with
     *                                                           the given name is
     *                                                           found (and the name
     *                                                           is not
     *                                                           'default').
     */
    SchedulingConfig getConfig(TransactionSource transactionSource, String name);

    /**
     * Updates an existing hub configuration based on the provided command. This
     * method supports partial updates.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param command           An {@link UpdateSchedulingConfigCommand} containing
     *                          the fields to
     *                          update.
     * @return The updated {@link SchedulingConfig} entity.
     */
    SchedulingConfig saveConfig(TransactionSource transactionSource, UpdateSchedulingConfigCommand command);

    /**
     * Resolves and returns the appropriate hub configuration.
     * <p>
     * If a {@code configName} is provided, it retrieves that specific
     * configuration. If not, it falls back to the currently enabled
     * configuration, and finally to a default configuration if no other is
     * found.
     *
     * @param transactionSource The transaction source to resolve for.
     * @param configName        The optional name of the configuration to resolve.
     * @return The resolved {@link SchedulingConfig}. This method is
     *         designed to always return a valid configuration.
     */
    SchedulingConfig getResolvedConfig(TransactionSource transactionSource, String configName);

    /**
     * Retrieves a list of clean and sorted time zones.
     *
     * @return A list of {@link TimeZoneOption} containing ID and display name.
     */
    List<TimeZoneOption> getTimeZones();

    // --- API Wrapper Methods (Anti-Corruption Layer) ---

    default SchedulingConfigResult createConfigResult(CreateSchedulingConfigCommand command) {
        return SchedulingConfigResult.fromEntity(this.createConfig(command));
    }

    default SchedulingConfigResult getConfigResult(TransactionSource transactionSource, String name) {
        return SchedulingConfigResult.fromEntity(this.getConfig(transactionSource, name));
    }

    default SchedulingConfigResult getResolvedConfigResult(TransactionSource transactionSource, String name) {
        return SchedulingConfigResult.fromEntity(this.getResolvedConfig(transactionSource, name));
    }

    default SchedulingConfigResult saveConfigResult(TransactionSource transactionSource,
            UpdateSchedulingConfigCommand command) {
        return SchedulingConfigResult.fromEntity(this.saveConfig(transactionSource, command));
    }
}
