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

import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NisaContributionSettingRepository extends JpaRepository<NisaContributionSetting, String> {

    @Query("SELECT DISTINCT s.recordDate FROM NisaContributionSetting s ORDER BY s.recordDate DESC LIMIT 1")
    Optional<LocalDate> findLatestRecordDate();

    List<NisaContributionSetting> findByRecordDate(LocalDate recordDate);

    List<NisaContributionSetting> findAllByOrderByRecordDateDesc();

    Optional<NisaContributionSetting> findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(String accountType,
            String fundCode);

    /**
     * Finds a NISA contribution setting matching the unique business key for a
     * given date.
     *
     * @param recordDate  The record date.
     * @param accountType The account type.
     * @param fundCode    The official fund code (投信協会コード).
     * @param course      The course.
     * @return The matching contribution setting, if any.
     */
    Optional<NisaContributionSetting> findByRecordDateAndAccountTypeAndFundCodeAndCourse(
            LocalDate recordDate, String accountType, String fundCode, String course);
}
