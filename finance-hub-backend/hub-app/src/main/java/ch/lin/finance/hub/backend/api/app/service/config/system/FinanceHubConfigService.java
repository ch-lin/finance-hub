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
package ch.lin.finance.hub.backend.api.app.service.config.system;

import ch.lin.finance.hub.backend.api.app.service.config.system.command.CreateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.UpdateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.FinanceHubConfigResult;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

/**
 * Defines the service layer contract for managing global Finance Hub
 * configurations.
 */
public interface FinanceHubConfigService {

    /**
     * Retrieves a summary of all available global configurations.
     * <p>
     * Ensures that at least one configuration exists (creating a default if
     * necessary),
     * and identifies which one is currently enabled.
     *
     * @return An {@link AllConfigsData} object detailing all config names and the
     *         active one.
     */
    AllConfigsData getAllConfigs();

    /**
     * Creates and persists a new global hub configuration.
     * <p>
     * If the new configuration is set to enabled, any previously enabled
     * configurations
     * are automatically disabled to enforce a single-active rule.
     *
     * @param command The data required to create a new configuration.
     * @return The freshly created {@link FinanceHubConfig} entity.
     */
    FinanceHubConfig createConfig(CreateFinanceHubConfigCommand command);

    /**
     * Deletes all global configurations from the database.
     * <p>
     * Warning: This is a destructive operation typically only used in cleanup
     * scenarios.
     */
    void deleteAllConfigs();

    /**
     * Deletes a specific global configuration by its unique name.
     * <p>
     * If the deleted configuration was the currently active one, the system will
     * automatically fall back to enabling the default configuration.
     *
     * @param name The name of the configuration to delete.
     */
    void deleteConfig(String name);

    /**
     * Retrieves a specific global configuration by its unique name.
     *
     * @param name The name of the configuration to look up.
     * @return The requested {@link FinanceHubConfig} entity.
     * @throws ch.lin.platform.exception.ConfigNotFoundException If no configuration
     *                                                           is found with the
     *                                                           given name.
     */
    FinanceHubConfig getConfig(String name);

    /**
     * Updates an existing global hub configuration or creates a new one if it
     * doesn't exist.
     * <p>
     * This method supports partial updates (i.e., only explicitly provided fields
     * are updated).
     * Like creation, if the config is marked as enabled, all others will be
     * disabled.
     *
     * @param command The data containing updates for an existing configuration.
     * @return The updated {@link FinanceHubConfig} entity.
     */
    FinanceHubConfig saveConfig(UpdateFinanceHubConfigCommand command);

    /**
     * Resolves and returns the appropriate global configuration.
     *
     * @param configName The optional name of the configuration to resolve.
     * @return The resolved {@link FinanceHubConfig}.
     */
    FinanceHubConfig getResolvedConfig(String configName);

    // --- API Wrapper Methods (Anti-Corruption Layer) ---

    default FinanceHubConfigResult getConfigResult(String name) {
        return FinanceHubConfigResult.fromEntity(this.getConfig(name));
    }

    default FinanceHubConfigResult createConfigResult(CreateFinanceHubConfigCommand command) {
        return FinanceHubConfigResult.fromEntity(this.createConfig(command));
    }

    default FinanceHubConfigResult saveConfigResult(UpdateFinanceHubConfigCommand command) {
        return FinanceHubConfigResult.fromEntity(this.saveConfig(command));
    }
}
