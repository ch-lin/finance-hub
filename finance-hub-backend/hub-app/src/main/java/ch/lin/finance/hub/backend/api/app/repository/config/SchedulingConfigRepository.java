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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Spring Data JPA repository for {@link SchedulingConfig} entities.
 * <p>
 * This interface provides standard CRUD operations and custom finder methods
 * for managing transaction source configurations (such as parsing settings, API
 * keys,
 * or automation schedules) for different data sources in the database.
 */
@Repository
public interface SchedulingConfigRepository extends JpaRepository<SchedulingConfig, Long> {

    /**
     * Deletes all records from the {@code scheduling_config} table.
     * <p>
     * This custom bulk delete operation is intended for cleanup or reset
     * purposes, directly executing a native-like JPA query.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM SchedulingConfig d")
    void cleanTable();

    /**
     * Finds all configurations that are currently active (enabled) across all
     * transaction sources.
     * <p>
     * Used by the orchestrator to initialize background jobs only for active
     * sources.
     *
     * @return A list of all enabled {@link SchedulingConfig} entities.
     */
    List<SchedulingConfig> findAllByEnabledTrue();

    /**
     * Finds the active configuration for a specific transaction source.
     * <p>
     * Typically, there should be at most one active configuration per source. This
     * method is used to fetch the current active strategy or scheduler settings.
     *
     * @param transactionSource The specific transaction source (e.g., AMAZON,
     *                          RAKUTEN).
     * @return An {@link Optional} containing the enabled
     *         {@link SchedulingConfig} for the source, or empty if none are
     *         enabled.
     */
    Optional<SchedulingConfig> findFirstByTransactionSourceAndEnabledTrue(TransactionSource transactionSource);

    /**
     * Finds all configurations associated with a specific transaction source,
     * regardless of whether they are enabled or disabled.
     *
     * @param transactionSource The transaction source to filter by.
     * @return A list of all {@link SchedulingConfig} entities linked to the source.
     */
    List<SchedulingConfig> findAllByTransactionSource(TransactionSource transactionSource);

    /**
     * Finds a configuration uniquely identified by its transaction source and
     * a specific string name.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param name              The descriptive name of the configuration to search
     *                          for.
     * @return An {@link Optional} containing the matched
     *         {@link SchedulingConfig}, or empty if not found.
     */
    Optional<SchedulingConfig> findByTransactionSourceAndName(TransactionSource transactionSource, String name);
}
