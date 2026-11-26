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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankStatementRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankTransactionRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class BankTransactionServiceImplTest {

    @Mock
    private BankStatementRepository statementRepository;

    @Mock
    private BankTransactionRepository transactionRepository;

    private BankTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BankTransactionServiceImpl(statementRepository, transactionRepository);
    }

    @Test
    void testSaveStatement_ExistingStatement_UpdatesFactAndPreservesCategory() {
        String bankName = "SMBC";
        String statementMonth = "2026-06";

        // Setup existing statement in DB
        BankStatement existingStatement = new BankStatement(bankName, statementMonth);
        LocalDate txDate = LocalDate.of(2026, 6, 15);
        BankTransaction existingTx = new BankTransaction(bankName, txDate, "Salary", 500000, 0, 1500000, "Old memo");
        TransactionCategory category = new TransactionCategory("Income", true, false, null);
        existingTx.setCategory(category);
        existingStatement.addTransaction(existingTx);

        when(statementRepository.findByBankNameAndStatementMonth(bankName, statementMonth))
                .thenReturn(Optional.of(existingStatement));
        when(statementRepository.save(any(BankStatement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, BankStatement.class));

        // Prepare incoming command with updated memo and a second new transaction
        SaveBankTransactionCommand txCmd1 = SaveBankTransactionCommand.builder()
                .transactionDate(txDate.toString())
                .description("Salary")
                .depositAmount(500000)
                .withdrawalAmount(0)
                .balance(1500000)
                .memo("June Bonus and Salary")
                .build();

        SaveBankTransactionCommand txCmd2 = SaveBankTransactionCommand.builder()
                .transactionDate("2026-06-20")
                .description("Groceries")
                .depositAmount(0)
                .withdrawalAmount(4000)
                .balance(1496000)
                .memo("Market")
                .build();

        SaveBankStatementCommand command = SaveBankStatementCommand.builder()
                .bankName(bankName)
                .statementMonth(statementMonth)
                .transactions(List.of(txCmd1, txCmd2))
                .build();

        BankStatementResult result = service.saveStatement(command);

        assertNotNull(result);
        assertEquals(2, existingStatement.getTransactions().size());

        // Verify existing transaction: memo updated, category preserved
        assertEquals("June Bonus and Salary", existingTx.getMemo());
        assertEquals(category, existingTx.getCategory());

        // Verify statement was saved
        verify(statementRepository).save(existingStatement);
    }

    @Test
    void testSaveStatement_NewStatement_CreatesAndSaves() {
        String bankName = "MUFG";
        String statementMonth = "2026-07";

        when(statementRepository.findByBankNameAndStatementMonth(bankName, statementMonth))
                .thenReturn(Optional.empty());
        when(statementRepository.save(any(BankStatement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, BankStatement.class));

        SaveBankTransactionCommand txCmd = SaveBankTransactionCommand.builder()
                .transactionDate("2026-07-01")
                .description("Coffee")
                .depositAmount(0)
                .withdrawalAmount(500)
                .balance(50000)
                .memo("Cafe")
                .build();

        SaveBankStatementCommand command = SaveBankStatementCommand.builder()
                .bankName(bankName)
                .statementMonth(statementMonth)
                .transactions(List.of(txCmd))
                .build();

        BankStatementResult result = service.saveStatement(command);

        assertNotNull(result);
        assertEquals(bankName, result.getBankName());
        assertEquals(statementMonth, result.getStatementMonth());
        assertEquals(1, result.getTransactions().size());
        verify(statementRepository).save(any(BankStatement.class));
    }
}
