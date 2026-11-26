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
package ch.lin.finance.hub.backend.api.app.service.management;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation.TransactionLinkRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.repository.job.ImportJobHistoryRepository;

/**
 * Transactional implementation of {@link DataCleanupService}.
 * <p>
 * Handles the order of table deletion to respect foreign key constraints
 * (e.g., deleting Links before Orders and Transactions), followed by sequence
 * resets.
 */
@Service
public class DataCleanupServiceImpl implements DataCleanupService {

    /** Repository for Amazon orders. */
    private final AmazonOrderRepository amazonOrderRepository;

    /** Repository for credit card transactions. */
    private final CreditCardTransactionRepository creditCardRepository;

    /** Repository for transaction links. */
    private final TransactionLinkRepository transactionLinkRepository;

    /** Repository for import job history. */
    private final ImportJobHistoryRepository importJobHistoryRepository;

    /**
     * Constructs a new DataCleanupServiceImpl.
     * 
     * @param amazonOrderRepository      The repository for Amazon orders.
     * @param creditCardRepository       The repository for Credit Card
     *                                   transactions.
     * @param transactionLinkRepository  The repository for Transaction Links.
     * @param importJobHistoryRepository The repository for Import Job Histories.
     */
    public DataCleanupServiceImpl(AmazonOrderRepository amazonOrderRepository,
            CreditCardTransactionRepository creditCardRepository,
            TransactionLinkRepository transactionLinkRepository,
            ImportJobHistoryRepository importJobHistoryRepository) {
        this.amazonOrderRepository = amazonOrderRepository;
        this.creditCardRepository = creditCardRepository;
        this.transactionLinkRepository = transactionLinkRepository;
        this.importJobHistoryRepository = importJobHistoryRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void cleanup() {
        // Delete dependent tables first
        transactionLinkRepository.cleanTable(); // Depends on AmazonOrder and CreditCardTransaction
        amazonOrderRepository.cleanTable();
        creditCardRepository.cleanTable();
        importJobHistoryRepository.cleanTable();

        transactionLinkRepository.resetSequence();
        amazonOrderRepository.resetSequence();
        creditCardRepository.resetSequence();
        importJobHistoryRepository.resetSequence();
    }
}
