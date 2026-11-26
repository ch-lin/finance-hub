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
package ch.lin.finance.hub.backend.api.app.repository.accounting.nisa;

import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing NISA snapshot records.
 */
@Repository
public interface NisaSnapshotRecordRepository extends JpaRepository<NisaSnapshotRecord, Long> {

    /**
     * Finds all NISA snapshot records ordered by record date descending.
     *
     * @return A list of all snapshot records.
     */
    List<NisaSnapshotRecord> findAllByOrderByRecordDateDesc();

    /**
     * Finds the most recent snapshot record to determine the latest date.
     *
     * @return The latest snapshot record, if any.
     */
    Optional<NisaSnapshotRecord> findFirstByOrderByRecordDateDesc();

    /**
     * Finds the most recent snapshot record for a specific fund code and account
     * type.
     *
     * @param accountType The account type.
     * @param fundCode    The official fund code (投信協会コード).
     * @return The latest snapshot record for this fund, if any.
     */
    Optional<NisaSnapshotRecord> findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(String accountType,
            String fundCode);

    /**
     * Finds all NISA snapshot records for a specific date.
     *
     * @param recordDate The date to fetch records for.
     * @return A list of snapshot records for the given date.
     */
    List<NisaSnapshotRecord> findByRecordDate(LocalDate recordDate);

    /**
     * Finds a NISA snapshot record matching the unique business key for a given
     * date.
     *
     * @param recordDate  The record date.
     * @param accountType The account type.
     * @param fundCode    The official fund code (投信協会コード).
     * @return The matching snapshot record, if any.
     */
    Optional<NisaSnapshotRecord> findByRecordDateAndAccountTypeAndFundCode(LocalDate recordDate, String accountType,
            String fundCode);
}
