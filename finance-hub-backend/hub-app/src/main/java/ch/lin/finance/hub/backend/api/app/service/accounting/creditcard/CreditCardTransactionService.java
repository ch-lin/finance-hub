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
package ch.lin.finance.hub.backend.api.app.service.accounting.creditcard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.command.CreateCreditCardTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;

/**
 * Service for handling Credit Card Transactions.
 */
public interface CreditCardTransactionService {

    /**
     * Retrieves a paginated list of credit card transactions with optional filters.
     * 
     * @param cardNames Filter by card names.
     * @param startDate Filter by transaction date (inclusive).
     * @param endDate   Filter by transaction date (inclusive).
     * @param pageable  Pagination information.
     * @return A paginated list of transactions.
     */
    Page<CreditCardTransactionResult> getTransactions(List<String> cardNames, LocalDate startDate, LocalDate endDate,
            Pageable pageable);

    /**
     * Retrieves a distinct list of available card names.
     *
     * @return A list of card names.
     */
    List<String> getAvailableCardNames();

    /**
     * Calculates the dynamically summed total spent for each card based on filters.
     *
     * @param cardNames Filter by card names.
     * @param startDate Filter by transaction date (inclusive).
     * @param endDate   Filter by transaction date (inclusive).
     * @return A map of card names to their total spent amount.
     */
    Map<String, Integer> getTotalAmounts(List<String> cardNames, LocalDate startDate, LocalDate endDate);

    /**
     * Adds a single credit card transaction manually, auto-tying it to an existing
     * or new statement.
     * 
     * @param request The DTO containing the transaction details.
     * @return The saved transaction.
     */
    CreditCardTransactionResult addTransaction(CreateCreditCardTransactionCommand command);
}
