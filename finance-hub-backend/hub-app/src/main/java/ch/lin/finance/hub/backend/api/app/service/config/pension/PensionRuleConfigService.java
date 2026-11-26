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
package ch.lin.finance.hub.backend.api.app.service.config.pension;

import ch.lin.finance.hub.backend.api.app.service.config.pension.command.CreatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.UpdatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;

/**
 * Service interface for managing Pension Rule configurations.
 */
public interface PensionRuleConfigService {

    /**
     * Retrieves all configuration names and the name of the currently enabled one.
     */
    AllPensionRuleConfigsData getAllConfigs();

    /**
     * Retrieves a configuration by its name.
     */
    PensionRuleConfigResult getConfig(String name);

    /**
     * Creates a new pension rule configuration.
     */
    PensionRuleConfigResult createConfig(CreatePensionRuleConfigCommand command);

    /**
     * Updates an existing pension rule configuration.
     */
    PensionRuleConfigResult updateConfig(String name, UpdatePensionRuleConfigCommand command);

    /**
     * Deletes a pension rule configuration by its name.
     */
    void deleteConfig(String name);
}
