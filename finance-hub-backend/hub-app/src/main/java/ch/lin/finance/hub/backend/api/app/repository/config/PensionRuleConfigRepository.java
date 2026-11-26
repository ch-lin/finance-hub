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
package ch.lin.finance.hub.backend.api.app.repository.config;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.lin.finance.hub.backend.api.domain.model.config.pension.PensionRuleConfig;

/**
 * Repository interface for managing {@link PensionRuleConfig} entities.
 */
public interface PensionRuleConfigRepository extends JpaRepository<PensionRuleConfig, Long> {

    /**
     * Finds the first enabled pension rule configuration.
     *
     * @return an {@link Optional} containing the enabled configuration.
     */
    Optional<PensionRuleConfig> findFirstByEnabledTrue();

    /**
     * Finds all enabled configurations (useful for setting them to false).
     */
    java.util.List<PensionRuleConfig> findAllByEnabledTrue();

    /**
     * Finds a pension rule configuration by its name.
     *
     * @param name the name of the configuration to find
     * @return an {@link Optional} containing the configuration, or empty if not
     *         found.
     */
    Optional<PensionRuleConfig> findByName(String name);
}
