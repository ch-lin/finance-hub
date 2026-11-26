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

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

/**
 * Spring Data JPA repository for {@link FinanceHubConfig} entities.
 * <p>
 * Handles standard CRUD operations for the global, application-wide Finance Hub
 * configurations.
 * These configurations encompass settings like the system time zone or other
 * master properties.
 */
@Repository
public interface FinanceHubConfigRepository extends JpaRepository<FinanceHubConfig, Long> {

    /**
     * Finds a global Finance Hub configuration by its unique name.
     *
     * @param name The unique name identifier of the configuration.
     * @return An {@link Optional} containing the matched {@link FinanceHubConfig},
     *         or empty if it does not exist.
     */
    Optional<FinanceHubConfig> findByName(String name);

    /**
     * Retrieves the first enabled global Finance Hub configuration.
     * <p>
     * Useful when the system expects at most one active master configuration at any
     * given time.
     *
     * @return An {@link Optional} containing the first active
     *         {@link FinanceHubConfig},
     *         or empty if no enabled configuration exists.
     */
    Optional<FinanceHubConfig> findFirstByEnabledTrue();

    /**
     * Retrieves a list of all currently enabled global configurations.
     *
     * @return A list of enabled {@link FinanceHubConfig} entities.
     */
    List<FinanceHubConfig> findAllByEnabledTrue();

}
