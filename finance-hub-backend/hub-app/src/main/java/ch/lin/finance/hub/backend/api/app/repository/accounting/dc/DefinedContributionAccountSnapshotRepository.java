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
package ch.lin.finance.hub.backend.api.app.repository.accounting.dc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;

@Repository
public interface DefinedContributionAccountSnapshotRepository
        extends JpaRepository<DefinedContributionAccountSnapshot, Long> {

    @Modifying
    @Transactional
    void deleteByRecordDateAndType(LocalDate recordDate, DefinedContributionType type);

    @EntityGraph(attributePaths = { "funds" })
    Optional<DefinedContributionAccountSnapshot> findByRecordDateAndType(LocalDate recordDate,
            DefinedContributionType type);

    @EntityGraph(attributePaths = { "funds" })
    List<DefinedContributionAccountSnapshot> findAllByOrderByRecordDateDesc();

    Optional<DefinedContributionAccountSnapshot> findFirstByOrderByRecordDateDesc();

    Optional<DefinedContributionAccountSnapshot> findFirstByTypeOrderByRecordDateDesc(DefinedContributionType type);

    List<DefinedContributionAccountSnapshot> findByRecordDate(LocalDate recordDate);

    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE dc_account_snapshot AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();
}
