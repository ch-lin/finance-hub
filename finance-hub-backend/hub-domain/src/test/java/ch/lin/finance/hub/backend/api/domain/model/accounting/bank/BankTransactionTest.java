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
package ch.lin.finance.hub.backend.api.domain.model.accounting.bank;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BankTransactionTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        BankTransaction tx = new BankTransaction("SMBC", date, "Amazon", 0, 1500, 100000);

        assertEquals("SMBC", tx.getBankName());
        assertEquals(date, tx.getTransactionDate());
        assertEquals("Amazon", tx.getDescription());
        assertEquals(0, tx.getDepositAmount());
        assertEquals(1500, tx.getWithdrawalAmount());
        assertEquals(100000, tx.getBalance());
        
        tx.setMemo("Test memo");
        assertEquals("Test memo", tx.getMemo());
    }
    
    @Test
    void testEqualsAndHashCode() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        BankTransaction tx1 = new BankTransaction("SMBC", date, "Amazon", 0, 1500, 100000);
        BankTransaction tx2 = new BankTransaction("SMBC", date, "Amazon", 0, 1500, 100000);
        BankTransaction tx3 = new BankTransaction("SMBC", date, "Salary", 500000, 0, 600000);
        
        assertEquals(tx1, tx2);
        assertNotEquals(tx1, tx3);
        assertEquals(tx1.hashCode(), tx2.hashCode());
        assertNotEquals(tx1.hashCode(), tx3.hashCode());
    }

    @Test
    void testConstructorWithMemo() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        BankTransaction tx = new BankTransaction("SMBC", date, "Amazon", 0, 1500, 100000, "Prime Video");

        assertEquals("SMBC", tx.getBankName());
        assertEquals(date, tx.getTransactionDate());
        assertEquals("Amazon", tx.getDescription());
        assertEquals(0, tx.getDepositAmount());
        assertEquals(1500, tx.getWithdrawalAmount());
        assertEquals(100000, tx.getBalance());
        assertEquals("Prime Video", tx.getMemo());
    }

    @Test
    void testMerge_OverwritesAllParsedFactFieldsAndPreservesCategory() {
        LocalDate date1 = LocalDate.of(2025, 1, 1);
        BankTransaction existingTx = new BankTransaction("SMBC", date1, "Amazon", 0, 1500, 100000, "Old memo");

        ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory category =
                new ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory("Shopping", true, false, null);
        existingTx.setCategory(category);

        LocalDate date2 = LocalDate.of(2025, 1, 2);
        BankTransaction incomingTx = new BankTransaction("SMBC_UPDATED", date2, "Amazon JP", 100, 2000, 98000, "New memo");

        existingTx.merge(incomingTx);

        // All 7 fact fields are overwritten with incoming parsed facts
        assertEquals("SMBC_UPDATED", existingTx.getBankName());
        assertEquals(date2, existingTx.getTransactionDate());
        assertEquals("Amazon JP", existingTx.getDescription());
        assertEquals(100, existingTx.getDepositAmount());
        assertEquals(2000, existingTx.getWithdrawalAmount());
        assertEquals(98000, existingTx.getBalance());
        assertEquals("New memo", existingTx.getMemo());

        // User-defined field is strictly preserved
        assertSame(category, existingTx.getCategory());

        // Null safe
        existingTx.merge(null);
        assertEquals("New memo", existingTx.getMemo());
    }
}
