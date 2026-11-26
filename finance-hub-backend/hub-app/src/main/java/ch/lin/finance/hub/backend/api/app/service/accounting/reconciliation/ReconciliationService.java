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
package ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.ReconciliationAmazonOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.SuggestedMatchResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

/**
 * Interface for Reconciliation Service.
 */
public interface ReconciliationService {

    /**
     * Retrieves all credit card transactions that are related to Amazon but
     * have not yet been linked to any Amazon Order.
     *
     * @param pageable The pagination information.
     * @return A page of unmatched Amazon credit card transactions.
     */
    Page<CreditCardTransactionResult> getUnmatchedAmazonTransactions(Pageable pageable);

    /**
     * Retrieves a list of suggested Amazon Orders for a specific credit card
     * transaction.
     *
     * @param transactionId The ID of the unmatched credit card transaction.
     * @return A list of candidate Amazon Orders with match context.
     */
    List<SuggestedMatchResult> getSuggestions(Long transactionId);

    /**
     * Finds possible Amazon Order candidates for a given credit card
     * transaction using fuzzy matching logic (date proximity and unreconciled
     * balance).
     *
     * @param transaction The unmatched credit card transaction.
     * @return A list of candidate Amazon Orders with match context.
     */
    List<SuggestedMatchResult> findFuzzyMatches(CreditCardTransaction transaction);

    /**
     * Links an unmatched credit card transaction to an Amazon Order.
     *
     * @param creditCardTransactionId The ID of the credit card transaction.
     * @param amazonOrderId           The ID of the Amazon Order.
     */
    void linkTransaction(Long creditCardTransactionId, Long amazonOrderId);

    /**
     * Searches for unmatched Amazon orders.
     *
     * @param orderId   Optional Amazon order ID.
     * @param minAmount Optional minimum amount.
     * @param maxAmount Optional maximum amount.
     * @return A list of matching Amazon orders.
     */
    List<ReconciliationAmazonOrderResult> searchUnmatchedAmazonOrders(
            String orderId, java.math.BigDecimal minAmount, java.math.BigDecimal maxAmount);
}
