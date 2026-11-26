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
package ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation.TransactionLink;

/**
 * Spring Data JPA repository for {@link TransactionLink} entities.
 * <p>
 * This interface provides the mechanism to store and query reconciliation links
 * between different data sources, such as linking a Credit Card charge to a
 * specific
 * Amazon shopping order.
 */
@Repository
public interface TransactionLinkRepository extends JpaRepository<TransactionLink, Long> {

    /**
     * Deletes all records from the 'transaction_link' table.
     * <p>
     * Used primarily for administrative resets or database teardown sequences.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM TransactionLink tl")
    void cleanTable();

    /**
     * Resets the auto-increment sequence for the 'transaction_link' table.
     * <p>
     * <b>Note:</b> This uses a native MySQL syntax {@code ALTER TABLE} query.
     */
    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE transaction_link AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();

    /**
     * Finds all transaction links associated with a specific credit card
     * transaction.
     * <p>
     * Useful for checking if a credit card charge has already been matched,
     * or for retrieving the shopping orders that explain the credit card charge.
     *
     * @param creditCardTransactionId The internal database ID of the credit card
     *                                transaction.
     * @return A list of {@link TransactionLink} entities mapped to the specified
     *         credit card transaction.
     */
    List<TransactionLink> findByCreditCardTransactionId(Long creditCardTransactionId);

    /**
     * Finds all transaction links associated with a specific Amazon order.
     * <p>
     * Useful for checking if an Amazon order has already been reconciled against
     * a credit card statement.
     *
     * @param amazonOrderId The internal database ID of the Amazon order.
     * @return A list of {@link TransactionLink} entities mapped to the specified
     *         Amazon order.
     */
    List<TransactionLink> findByAmazonOrderId(Long amazonOrderId);

    /**
     * Finds all transaction links associated with a list of credit card transaction
     * IDs.
     *
     * @param creditCardTransactionIds A list of internal database IDs for credit
     *                                 card transactions.
     * @return A list of {@link TransactionLink} entities mapped to the specified
     *         credit card transactions.
     */
    List<TransactionLink> findByCreditCardTransactionIdIn(List<Long> creditCardTransactionIds);
}
