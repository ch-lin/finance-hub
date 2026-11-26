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
package ch.lin.finance.hub.backend.api.app.service.accounting.bank;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.CreateBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;
import java.util.Optional;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;

/**
 * Service for handling Bank Transactions and Statements.
 */
public interface BankTransactionService {

    /**
     * Retrieves a bank statement by bank name and statement month.
     * 
     * @param bankName       The name of the bank.
     * @param statementMonth The statement month (e.g., "2024-01").
     * @return An Optional containing the statement result if found.
     */
    Optional<BankStatementResult> getStatement(String bankName, String statementMonth);

    /**
     * Saves or updates a bank statement and its transactions.
     * 
     * @param command The command containing statement and transaction details.
     * @return The saved statement as a result object.
     */
    BankStatementResult saveStatement(SaveBankStatementCommand command);

    /**
     * Retrieves a paginated list of bank transactions with optional filters.
     * 
     * @param bankNames Filter by bank names.
     * @param startDate Filter by transaction date (inclusive).
     * @param endDate   Filter by transaction date (inclusive).
     * @param isIncome  Filter by whether it is income (deposit) or expense
     *                  (withdrawal).
     * @param pageable  Pagination information.
     * @return A paginated list of transactions as result objects.
     */
    Page<BankTransactionResult> getTransactions(List<String> bankNames, LocalDate startDate, LocalDate endDate,
            Boolean isIncome,
            Pageable pageable);

    /**
     * Retrieves the latest balance for all known banks.
     * 
     * @return A map of bank names to their latest passbook balance.
     */
    Map<String, Integer> getLatestBankBalances();

    /**
     * Adds a single bank transaction manually, auto-tying it to an existing or new
     * statement.
     * 
     * @param command The DTO containing the transaction details.
     * @return The saved transaction as a result object.
     */
    BankTransactionResult addTransaction(CreateBankTransactionCommand command);
}
