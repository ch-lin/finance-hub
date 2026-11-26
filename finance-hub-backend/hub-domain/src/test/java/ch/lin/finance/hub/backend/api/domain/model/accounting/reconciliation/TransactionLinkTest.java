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
package ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation;

import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TransactionLinkTest {

    @Test
    void testConstructorAndGetters() {
        CreditCardTransaction tx = new CreditCardTransaction("Visa", LocalDate.now(), "Amazon", 1000);
        AmazonOrder order = new AmazonOrder("A123", LocalDate.now());

        TransactionLink link = new TransactionLink(tx, order);

        assertEquals(tx, link.getCreditCardTransaction());
        assertEquals(order, link.getAmazonOrder());
    }
}
