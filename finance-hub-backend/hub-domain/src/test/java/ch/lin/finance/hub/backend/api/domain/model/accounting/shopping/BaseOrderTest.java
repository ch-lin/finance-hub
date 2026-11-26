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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

class BaseOrderTest {

    @Test
    void testFlagForReview() {
        // Use AmazonOrder as a concrete implementation of BaseOrder to test it
        AmazonOrder order = new AmazonOrder("A123", LocalDate.of(2025, 1, 1));

        assertFalse(order.getNeedsReview());
        assertNull(order.getReviewReason());

        order.flagForReview("Missing payment");

        assertTrue(order.getNeedsReview());
        assertEquals("Missing payment", order.getReviewReason());
    }

    @Test
    void testGetUnreconciledBalance() {
        AmazonOrder order = new AmazonOrder("A123", LocalDate.of(2025, 1, 1));
        order.setBilledAmount(new java.math.BigDecimal("1000.50"));
        order.setReconciledAmount(new java.math.BigDecimal("400.00"));
        
        java.math.BigDecimal balance = order.getUnreconciledBalance();
        
        assertEquals(new java.math.BigDecimal("600.50"), balance);
    }

    @Test
    void testGetUnreconciledBalance_ZeroReconciled() {
        AmazonOrder order = new AmazonOrder("A123", LocalDate.of(2025, 1, 1));
        order.setBilledAmount(new java.math.BigDecimal("150.00"));
        
        java.math.BigDecimal balance = order.getUnreconciledBalance();
        
        assertEquals(new java.math.BigDecimal("150.00"), balance);
    }
}
