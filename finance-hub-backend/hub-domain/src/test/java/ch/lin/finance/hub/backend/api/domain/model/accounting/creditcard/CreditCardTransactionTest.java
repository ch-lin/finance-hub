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
import org.junit.jupiter.api.Test;

class CreditCardTransactionTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate date = LocalDate.of(2025, 3, 1);
        CreditCardTransaction tx = new CreditCardTransaction("Visa", date, "Amazon", 500, "1回払い");

        assertEquals("Visa", tx.getCardName());
        assertEquals(date, tx.getTransactionDate());
        assertEquals("Amazon", tx.getStoreName());
        assertEquals(500, tx.getAmount());
        assertEquals("1回払い", tx.getMemo());
    }

    @Test
    void testMerge_OverwritesFactsAndPreservesCategory() {
        LocalDate date1 = LocalDate.of(2025, 3, 1);
        CreditCardTransaction existing = new CreditCardTransaction("Visa", date1, "Old Store", 500, "Old Memo");
        ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory category =
                new ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory("Shopping", true, false, null);
        existing.setCategory(category);

        LocalDate date2 = LocalDate.of(2025, 3, 2);
        CreditCardTransaction incoming = new CreditCardTransaction("MasterCard", date2, "New Store", 800, null);

        existing.merge(incoming);

        assertEquals("MasterCard", existing.getCardName());
        assertEquals(date2, existing.getTransactionDate());
        assertEquals("New Store", existing.getStoreName());
        assertEquals(800, existing.getAmount());
        assertEquals(null, existing.getMemo(), "Incoming null memo should overwrite existing memo (zero over-protection)");
        assertEquals(category, existing.getCategory(), "User-assigned category must be preserved");

        // Null incoming should be handled safely
        existing.merge(null);
        assertEquals("MasterCard", existing.getCardName());
    }
}
