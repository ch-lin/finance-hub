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
package ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Spring Data JPA repository for {@link CreditCardTransaction} entities.
 * <p>
 * This interface facilitates advanced queries and standard CRUD operations for
 * all
 * imported credit card transactions.
 */
@Repository
public interface CreditCardTransactionRepository
        extends JpaRepository<CreditCardTransaction, Long>, JpaSpecificationExecutor<CreditCardTransaction> {

    /**
     * Deletes all records from the 'credit_card_transaction' table.
     * <p>
     * Native JPA bulk deletion method used primarily during administrative resets.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM CreditCardTransaction cct")
    void cleanTable();

    /**
     * Resets the sequence for the 'credit_card_transaction' table's primary key.
     * <p>
     * <b>Note:</b> Alters the MySQL {@code AUTO_INCREMENT} state to 1.
     */
    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE credit_card_transaction AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();

    /**
     * Finds all credit card transactions occurring within a specified, inclusive
     * date range.
     * <p>
     * Often utilized when compiling monthly expense reports or filtering
     * transactions
     * prior to reconciliation.
     *
     * @param startDate The start date (inclusive) of the transaction boundary.
     * @param endDate   The end date (inclusive) of the transaction boundary.
     * @return A list of {@link CreditCardTransaction} records within the date
     *         range.
     */
    List<CreditCardTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Finds all credit card transactions that appear to be Amazon purchases but
     * are not yet linked to an Amazon Order in the transaction_link table.
     * <p>
     * This query actively scans for the keyword "AMAZON" in either the store name
     * or the transaction memo. It then excludes any transactions that already have
     * a corresponding entry in the {@code TransactionLink} relational table. This
     * is
     * the core driver query for the reconciliation engine.
     *
     * @param pageable The pagination and sorting parameters.
     * @return A {@link Page} of unmatched Amazon-related credit card transactions.
     */
    @Query("SELECT c FROM CreditCardTransaction c "
            + "WHERE (UPPER(c.storeName) LIKE '%AMAZON%' OR UPPER(c.memo) LIKE '%AMAZON%') "
            + "AND NOT EXISTS (SELECT 1 FROM TransactionLink t WHERE t.creditCardTransaction.id = c.id)")
    Page<CreditCardTransaction> findUnmatchedAmazonTransactions(Pageable pageable);

    /**
     * Finds all distinct credit card names available in the transactions.
     *
     * @return A list of distinct card names.
     */
    @Query("SELECT DISTINCT t.cardName FROM CreditCardTransaction t")
    List<String> findDistinctCardNames();
}
