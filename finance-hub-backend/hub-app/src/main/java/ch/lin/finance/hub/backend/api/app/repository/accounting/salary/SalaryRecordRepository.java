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
package ch.lin.finance.hub.backend.api.app.repository.accounting.salary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType;

/**
 * Repository interface for managing {@link SalaryRecord} entities.
 */
@Repository
public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {

    /**
     * Finds a salary record by its payment date.
     * 
     * @param paymentDate the payment date
     * @return an Optional containing the salary record, or empty if not found
     */
    Optional<SalaryRecord> findByPaymentDate(LocalDate paymentDate);

    /**
     * Finds all salary records ordered by payment date descending.
     * 
     * @return a list of salary records
     */
    java.util.List<SalaryRecord> findAllByOrderByPaymentDateDesc();

    /**
     * Finds the most recent salary record by type.
     * 
     * @param type the type of the salary record
     * @return an Optional containing the salary record, or empty if not found
     */
    Optional<SalaryRecord> findFirstByTypeOrderByPaymentDateDesc(SalaryType type);

    List<SalaryRecord> findAllByOwnerUserIdOrderByPaymentDateDesc(String ownerUserId);

    Optional<SalaryRecord> findFirstByOwnerUserIdAndTypeOrderByPaymentDateDesc(String ownerUserId, SalaryType type);
}
