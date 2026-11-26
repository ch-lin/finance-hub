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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AmazonOrderTest {

    @Test
    void testConstructorAndDefaultValues() {
        LocalDate date = LocalDate.of(2025, 3, 1);
        AmazonOrder order = new AmazonOrder("A123", date);

        assertEquals("A123", order.getOrderId());
        assertEquals("AMAZON_JP", order.getOrderSource());
        assertEquals(date, order.getOrderDate());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());
        assertEquals(BigDecimal.ZERO, order.getGiftCardAmount());
        assertEquals(BigDecimal.ZERO, order.getDPointsUsed());
    }

    @Test
    void testRelationships() {
        AmazonOrder order = new AmazonOrder("A123", LocalDate.now());

        AmazonItem item = new AmazonItem("Book");
        order.addItem(item);
        assertTrue(order.getItems().contains(item));
        assertEquals(order, item.getOrder());

        AmazonPayment payment = new AmazonPayment("Visa");
        order.addPayment(payment);
        assertTrue(order.getPayments().contains(payment));
        assertEquals(order, payment.getOrder());

        AmazonShipment shipment = new AmazonShipment(LocalDate.now(), "Yamato", "1234");
        order.addShipment(shipment);
        assertTrue(order.getShipments().contains(shipment));
        assertEquals(order, shipment.getOrder());
    }

    @Test
    void testTotalDeductions() {
        AmazonOrder order = new AmazonOrder("A123", LocalDate.now());
        order.setPointsUsed(new BigDecimal("100"));
        order.setGiftCardAmount(new BigDecimal("200"));
        order.setDPointsUsed(new BigDecimal("50"));
        order.setDiscounts(new BigDecimal("10"));

        assertEquals(new BigDecimal("360"), order.getTotalDeductions());
    }

    @Test
    void testUnreconciledBalance() {
        AmazonOrder order = new AmazonOrder("A123", LocalDate.now());
        order.setBilledAmount(new BigDecimal("1000"));
        order.setReconciledAmount(new BigDecimal("400"));

        assertEquals(new BigDecimal("600"), order.getUnreconciledBalance());
    }

    @Test
    void testMerge_OverwritesFactsPreservesReconciledAmountAndUserFields() {
        LocalDate orderDate = LocalDate.of(2025, 3, 1);
        AmazonOrder original = new AmazonOrder("A123", orderDate);
        original.setShippingDate(LocalDate.of(2025, 3, 2));
        original.setReconciledAmount(new BigDecimal("200"));

        // Item 1: Has user-managed category and privacy override
        AmazonItem existingItem1 = new AmazonItem("Existing Book");
        existingItem1.setUnitPrice(new BigDecimal("1500"));
        existingItem1.setQuantity(1);
        existingItem1.setSeller("Old Seller");
        ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory cat =
                new ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory("Books", true, false, null);
        existingItem1.setCategory(cat);
        existingItem1.setPrivacyLevel(ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel.PRIVATE);
        original.addItem(existingItem1);

        // Item 2: Unmatched existing item (should be preserved)
        AmazonItem existingItem2 = new AmazonItem("Standalone Pen");
        existingItem2.setUnitPrice(new BigDecimal("300"));
        original.addItem(existingItem2);

        // Incoming order from statement / extension re-download with updated orderDate
        LocalDate updatedOrderDate = LocalDate.of(2025, 3, 3);
        AmazonOrder parsed = new AmazonOrder("A123", updatedOrderDate);
        parsed.setTotalAmount(new BigDecimal("2000"));
        parsed.setShippingDate(LocalDate.of(2025, 3, 5)); // Updated shipping date must overwrite

        // Incoming match for Item 1 (same name and unit price) with updated quantity and seller
        AmazonItem incomingItem1 = new AmazonItem("Existing Book");
        incomingItem1.setUnitPrice(new BigDecimal("1500"));
        incomingItem1.setQuantity(2);
        incomingItem1.setSeller("New Seller");
        incomingItem1.setItemId("B00123");
        parsed.addItem(incomingItem1);

        // Incoming new Item 3
        AmazonItem incomingItem3 = new AmazonItem("New Notebook");
        incomingItem3.setUnitPrice(new BigDecimal("500"));
        parsed.addItem(incomingItem3);

        // Act
        original.merge(parsed);

        // Assert: Facts updated
        assertEquals(updatedOrderDate, original.getOrderDate(), "Order date must be overwritten");
        assertEquals(new BigDecimal("2000"), original.getTotalAmount());
        assertEquals(LocalDate.of(2025, 3, 5), original.getShippingDate(), "Shipping date must be overwritten");
        assertEquals(new BigDecimal("200"), original.getReconciledAmount(), "Reconciled amount must be preserved");

        // Assert: Items merged loosely without deletion
        assertEquals(3, original.getItems().size(), "Must preserve unmatched existing items + matched + new items");

        AmazonItem mergedItem1 = original.getItems().stream()
                .filter(i -> "Existing Book".equals(i.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedItem1);
        assertEquals(2, mergedItem1.getQuantity());
        assertEquals("New Seller", mergedItem1.getSeller());
        assertEquals("B00123", mergedItem1.getItemId());
        assertEquals(cat, mergedItem1.getCategory(), "User-assigned category must be strictly preserved");
        assertEquals(ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel.PRIVATE, mergedItem1.getPrivacyLevel(),
                "User-assigned privacyLevel must be strictly preserved");

        // Null incoming handled safely
        original.merge(null);
        assertEquals(new BigDecimal("2000"), original.getTotalAmount());
    }

    @Test
    void testNoArgsConstructor() {
        AmazonOrder order = new AmazonOrder();
        assertNotNull(order);
    }

    @Test
    void testFlagForReview() {
        AmazonOrder order = new AmazonOrder("A123", LocalDate.now());

        assertFalse(order.getNeedsReview());
        assertNull(order.getReviewReason());

        order.flagForReview("Math mismatch");

        assertTrue(order.getNeedsReview());
        assertEquals("Math mismatch", order.getReviewReason());
    }
}
