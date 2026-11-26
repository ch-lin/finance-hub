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
package ch.lin.finance.hub.backend.api.app.repository.accounting.pension;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;

/**
 * Repository interface for managing {@link PensionRecord} entities.
 */
@Repository
public interface PensionRecordRepository extends JpaRepository<PensionRecord, Long> {

    /**
     * Finds a pension record by its record date.
     * 
     * @param recordDate The record date to search for.
     * @return An Optional containing the record if found, or empty if not.
     */
    Optional<PensionRecord> findByRecordDate(LocalDate recordDate);
}
