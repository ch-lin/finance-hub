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

import java.util.ArrayList;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;

/**
 * Encapsulates the results of parsing a bank statement file.
 */
public class BankParseResult {

    /**
     * The list of successfully parsed bank transactions.
     */
    private final List<BankTransaction> transactions;

    /**
     * Constructs a new empty parse result.
     */
    public BankParseResult() {
        this.transactions = new ArrayList<>();
    }

    /**
     * Adds a transaction to the result if it is not null.
     * 
     * @param transaction The transaction to add
     */
    public void addTransaction(BankTransaction transaction) {
        if (transaction != null) {
            transactions.add(transaction);
        }
    }

    /**
     * Returns the list of parsed transactions.
     * 
     * @return List of {@link BankTransaction}
     */
    public List<BankTransaction> getTransactions() {
        return transactions;
    }
}
