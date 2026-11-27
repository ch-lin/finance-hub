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
package ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;

/**
 * Repository for accessing MortgageRecord entities.
 */
@Repository
public interface MortgageRecordRepository extends JpaRepository<MortgageRecord, Long> {

    List<MortgageRecord> findByOwnerUserIdAndTaxDeductionEligibleTrue(String ownerUserId);

    Optional<MortgageRecord> findByBankNameAndContractNumber(String bankName, String contractNumber);

    List<MortgageRecord> findAllByOwnerUserId(String ownerUserId);
}
