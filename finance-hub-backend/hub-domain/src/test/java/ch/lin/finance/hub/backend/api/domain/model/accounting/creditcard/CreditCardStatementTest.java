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
package ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CreditCardStatementTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate paymentDate = LocalDate.of(2025, 4, 10);
        CreditCardStatement statement = new CreditCardStatement("Visa", "2025-03", paymentDate, 1000);

        assertEquals("Visa", statement.getCardName());
        assertEquals("2025-03", statement.getBillingMonth());
        assertEquals(paymentDate, statement.getPaymentDate());
        assertEquals(1000, statement.getTotalAmount());
        assertNotNull(statement.getTransactions());
        assertTrue(statement.getTransactions().isEmpty());
    }

    @Test
    void testAddTransactionSetsBackReference() {
        CreditCardStatement statement = new CreditCardStatement("Visa", "2025-03", LocalDate.of(2025, 4, 10), 1000);
        CreditCardTransaction tx = new CreditCardTransaction("Visa", LocalDate.of(2025, 3, 1), "Store", 500);

        statement.addTransaction(tx);

        assertEquals(1, statement.getTransactions().size());
        assertEquals(statement, tx.getStatement());
    }

    @Test
    void testMerge_OverwritesFactsAndPreservesBankTransactionId() {
        CreditCardStatement existing = new CreditCardStatement("Visa", "2025-03", LocalDate.of(2025, 4, 10), 1000);
        existing.setBankTransactionId("bank-tx-123");

        CreditCardStatement incoming = new CreditCardStatement("MasterCard", "2025-04", null, 2500);

        existing.merge(incoming);

        assertEquals("MasterCard", existing.getCardName());
        assertEquals("2025-04", existing.getBillingMonth());
        assertEquals(2500, existing.getTotalAmount());
        assertEquals(null, existing.getPaymentDate(), "Null payment date from incoming should overwrite existing value");
        assertEquals("bank-tx-123", existing.getBankTransactionId(), "System-reconciled bankTransactionId must be preserved");

        // Null incoming should be handled safely
        existing.merge(null);
        assertEquals("MasterCard", existing.getCardName());
    }

    @Test
    void testMergeTransactions_MatchedOverwritesFactsPreservesCategory_UnmatchedAdded_ZeroDeletion() {
        CreditCardStatement existing = new CreditCardStatement("Visa", "2025-03", LocalDate.of(2025, 4, 10), 1000);

        LocalDate date1 = LocalDate.of(2025, 3, 1);
        CreditCardTransaction tx1 = new CreditCardTransaction("Visa", date1, "Store A", 1000, "Old Memo");
        ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory cat1 =
                new ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory("Food", true, false, null);
        tx1.setCategory(cat1);
        existing.addTransaction(tx1);

        LocalDate date2 = LocalDate.of(2025, 3, 5);
        CreditCardTransaction tx2 = new CreditCardTransaction("Visa", date2, "Store B", 2000, "Preserved Memo");
        existing.addTransaction(tx2);

        // Incoming contains:
        // 1) Match for tx1: same date, same amount, same store, but new memo
        CreditCardTransaction incomingTx1 = new CreditCardTransaction("Visa", date1, "Store A", 1000, "New Memo");
        // 2) Completely new transaction tx3
        LocalDate date3 = LocalDate.of(2025, 3, 15);
        CreditCardTransaction incomingTx3 = new CreditCardTransaction("Visa", date3, "Store C", 3000, "New Tx");

        existing.mergeTransactions(java.util.List.of(incomingTx1, incomingTx3));

        // Total should be 3: tx1 (updated), tx2 (kept - zero deletion), tx3 (added)
        assertEquals(3, existing.getTransactions().size());

        CreditCardTransaction updatedTx1 = existing.getTransactions().stream()
                .filter(t -> t.getStoreName().equals("Store A"))
                .findFirst().orElseThrow();
        assertEquals("New Memo", updatedTx1.getMemo());
        assertEquals(cat1, updatedTx1.getCategory(), "Category on tx1 must be preserved");

        CreditCardTransaction keptTx2 = existing.getTransactions().stream()
                .filter(t -> t.getStoreName().equals("Store B"))
                .findFirst().orElseThrow();
        assertEquals(2000, keptTx2.getAmount());

        CreditCardTransaction addedTx3 = existing.getTransactions().stream()
                .filter(t -> t.getStoreName().equals("Store C"))
                .findFirst().orElseThrow();
        assertEquals(3000, addedTx3.getAmount());
        assertEquals(existing, addedTx3.getStatement(), "Back reference must be set on newly added transaction");
    }

    @Test
    void testMergeTransactions_NullIncomingList_DoesNothing() {
        CreditCardStatement statement = new CreditCardStatement("Visa", "2025-03", null, 1000);
        statement.mergeTransactions(null);
        assertTrue(statement.getTransactions().isEmpty());
    }
}
