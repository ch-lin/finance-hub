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
package ch.lin.finance.hub.backend.api.app.service.config.education;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.config.education.command.CreateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.UpdateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.result.EducationConfigResult;
import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;

public interface EducationConfigService {

    /**
     * Retrieves the default education configuration.
     *
     * @return EducationConfigResult containing the system default education
     * configuration
     */
    EducationConfigResult getDefaultConfig();

    /**
     * Retrieves the default education configuration as domain entity.
     *
     * @return EducationConfig domain entity
     */
    EducationConfig getDefaultDomainConfig();

    /**
     * Retrieves all education configurations.
     */
    List<EducationConfigResult> getAllConfigs();

    /**
     * Retrieves a configuration by its name.
     */
    EducationConfigResult getConfig(String name);

    /**
     * Creates a new education configuration.
     */
    EducationConfigResult createConfig(CreateEducationConfigCommand command);

    /**
     * Updates an existing education configuration.
     */
    EducationConfigResult updateConfig(String name, UpdateEducationConfigCommand command);

    /**
     * Deletes an education configuration by its name.
     */
    void deleteConfig(String name);

}
