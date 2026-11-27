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
package ch.lin.finance.hub.backend.api.app.repository.accounting.bank;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;

/**
 * Spring Data JPA repository for {@link BankTransaction} entities.
 */
@Repository
public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, String>, JpaSpecificationExecutor<BankTransaction> {

    /**
     * Retrieves a list of all distinct bank names from the transaction records.
     *
     * @return a list of distinct bank names
     */
    @Query("SELECT DISTINCT t.bankName FROM BankTransaction t")
    List<String> findDistinctBankNames();

    @Query("SELECT DISTINCT t.bankName FROM BankTransaction t WHERE t.ownerUserId = :ownerUserId")
    List<String> findDistinctBankNamesByOwnerUserId(@Param("ownerUserId") String ownerUserId);

    /**
     * Finds the most recent transaction for a specific bank based on the
     * transaction date.
     *
     * @param bankName the name of the bank
     * @return the most recent {@link BankTransaction} for the specified bank
     */
    BankTransaction findFirstByBankNameOrderByTransactionDateDesc(String bankName);

    BankTransaction findFirstByOwnerUserIdAndBankNameOrderByTransactionDateDesc(String ownerUserId, String bankName);

    /**
     * Finds all bank transactions occurring within a specified, inclusive date
     * range.
     *
     * @param startDate The start date (inclusive) of the transaction boundary.
     * @param endDate   The end date (inclusive) of the transaction boundary.
     * @return A list of {@link BankTransaction} records within the date range.
     */
    List<BankTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);
}
