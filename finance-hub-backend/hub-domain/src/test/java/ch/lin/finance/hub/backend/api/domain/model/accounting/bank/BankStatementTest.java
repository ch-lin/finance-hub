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

class BankStatementTest {

    @Test
    void testConstructorAndGetters() {
        BankStatement statement = new BankStatement("SMBC", "2025-01");
        
        assertEquals("SMBC", statement.getBankName());
        assertEquals("2025-01", statement.getStatementMonth());
        assertTrue(statement.getTransactions().isEmpty());
    }
    
    @Test
    void testAddAndRemoveTransaction() {
        BankStatement statement = new BankStatement("SMBC", "2025-01");
        BankTransaction tx = new BankTransaction("SMBC", LocalDate.of(2025, 1, 1), "Amazon", 0, 1000, 50000);
        
        statement.addTransaction(tx);
        
        assertEquals(1, statement.getTransactions().size());
        assertEquals(statement, tx.getStatement());
        
        statement.removeTransaction(tx);
        
        assertTrue(statement.getTransactions().isEmpty());
        assertNull(tx.getStatement());
    }
    
    @Test
    void testEqualsAndHashCode() {
        BankStatement s1 = new BankStatement("SMBC", "2025-01");
        BankStatement s2 = new BankStatement("SMBC", "2025-01");
        BankStatement s3 = new BankStatement("MUFG", "2025-01");
        
        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1.hashCode(), s3.hashCode());
    }

    @Test
    void testMergeTransactions() {
        BankStatement statement = new BankStatement("SMBC", "2025-01");

        LocalDate date1 = LocalDate.of(2025, 1, 5);
        BankTransaction existingTx = new BankTransaction("SMBC", date1, "Restaurant", 0, 3000, 97000, "Old memo");
        ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory cat =
                new ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory("Dining", true, false, null);
        existingTx.setCategory(cat);
        statement.addTransaction(existingTx);

        // Prepare incoming list: one matching transaction with updated memo, one brand new transaction
        BankTransaction updatedIncoming = new BankTransaction("SMBC", date1, "Restaurant", 0, 3000, 97000, "Updated note from bank online portal");

        LocalDate date2 = LocalDate.of(2025, 1, 10);
        BankTransaction brandNewTx = new BankTransaction("SMBC", date2, "Supermarket", 0, 5000, 92000, "Groceries");

        int newAddedCount = statement.mergeTransactions(java.util.List.of(updatedIncoming, brandNewTx));

        // 1 new added, total size is 2
        assertEquals(1, newAddedCount);
        assertEquals(2, statement.getTransactions().size());

        // Existing transaction: memo updated, category preserved
        assertEquals("Updated note from bank online portal", existingTx.getMemo());
        assertSame(cat, existingTx.getCategory());

        // Brand new transaction: added with parent statement linked
        assertTrue(statement.getTransactions().contains(brandNewTx));
        assertEquals(statement, brandNewTx.getStatement());

        // Null / empty safe
        assertEquals(0, statement.mergeTransactions(null));
        assertEquals(0, statement.mergeTransactions(java.util.Collections.emptyList()));
        assertEquals(2, statement.getTransactions().size());
    }
}
