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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation.TransactionLinkRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.repository.job.ImportJobHistoryRepository;

@ExtendWith(MockitoExtension.class)
class DataCleanupServiceImplTest {

    @Mock
    private AmazonOrderRepository amazonOrderRepository;

    @Mock
    private CreditCardTransactionRepository creditCardRepository;

    @Mock
    private TransactionLinkRepository transactionLinkRepository;

    @Mock
    private ImportJobHistoryRepository importJobHistoryRepository;

    @InjectMocks
    private DataCleanupServiceImpl dataCleanupService;

    @Test
    void testCleanup() {
        dataCleanupService.cleanup();

        // Verify the order of cleanTable calls to respect foreign key constraints
        InOrder inOrder = inOrder(transactionLinkRepository, amazonOrderRepository, creditCardRepository,
                importJobHistoryRepository);

        inOrder.verify(transactionLinkRepository).cleanTable();
        inOrder.verify(amazonOrderRepository).cleanTable();
        inOrder.verify(creditCardRepository).cleanTable();
        inOrder.verify(importJobHistoryRepository).cleanTable();

        // Verify sequence resets
        verify(transactionLinkRepository).resetSequence();
        verify(amazonOrderRepository).resetSequence();
        verify(creditCardRepository).resetSequence();
        verify(importJobHistoryRepository).resetSequence();
    }
}
