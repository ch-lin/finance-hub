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

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import jakarta.transaction.Transactional;

@Repository
public interface DefinedContributionFundSnapshotRepository
        extends JpaRepository<DefinedContributionFundSnapshot, Long> {

    /**
     * Finds the most recent snapshot record for a specific fund and account type.
     */
    Optional<DefinedContributionFundSnapshot> findFirstByAccountSnapshotTypeAndFundNameOrderByAccountSnapshotRecordDateDesc(
            DefinedContributionType type, String fundName);

    /**
     * Finds the most recent snapshot record for a specific fund by commodity type
     * and fund name.
     */
    Optional<DefinedContributionFundSnapshot> findFirstByCommodityTypeAndFundNameOrderByAccountSnapshot_RecordDateDesc(
            String commodityType, String fundName);

    Optional<DefinedContributionFundSnapshot> findFirstByOwnerUserIdAndCommodityTypeAndFundNameOrderByAccountSnapshot_RecordDateDesc(
            String ownerUserId, String commodityType, String fundName);

    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE dc_fund_snapshot AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();
}
