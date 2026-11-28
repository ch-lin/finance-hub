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
package ch.lin.finance.hub.backend.api.app.repository.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;

/**
 * Spring Data JPA repository for {@link ImportJobHistory} entities.
 * <p>
 * Provides standard CRUD operations for import job historical records. It
 * tracks
 * the execution status, success/failure counts, and metadata for batch jobs
 * processing
 * CSV or API imports.
 */
@Repository
public interface ImportJobHistoryRepository extends JpaRepository<ImportJobHistory, Long> {

    /**
     * Deletes all records from the 'import_job_history' table.
     * <p>
     * This bulk delete operation bypasses the persistence context, performing a
     * direct
     * SQL {@code DELETE} statement. It is typically used for system resets or
     * testing.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM ImportJobHistory ijh")
    void cleanTable();

    /**
     * Resets the sequence for the 'import_job_history' table's primary key.
     * <p>
     * <b>Note:</b> This native SQL query resets the {@code AUTO_INCREMENT} value.
     * Use with caution, usually immediately following a {@link #cleanTable()} call.
     */
    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE import_job_history AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();

    /**
     * Retrieves a paginated list of import job histories for a specific
     * transaction source, ordered by start time in descending order (newest first).
     * <p>
     * This method is commonly used by the frontend to display the most recent
     * job executions and their outcomes for a given source (e.g., AMAZON or
     * CREDIT_CARD).
     *
     * @param transactionSource The specific transaction source to filter jobs by.
     * @param pageable          The pagination information containing page number
     *                          and size.
     * @return A {@link Page} containing the relevant {@link ImportJobHistory}
     *         records.
     */
    Page<ImportJobHistory> findAllByTransactionSourceOrderByStartTimeDesc(TransactionSource transactionSource,
            Pageable pageable);

    Page<ImportJobHistory> findAllByOwnerUserIdAndTransactionSourceOrderByStartTimeDesc(String ownerUserId,
            TransactionSource transactionSource, Pageable pageable);
}
