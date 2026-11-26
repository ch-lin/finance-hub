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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RakutenOrderTest {

    @Test
    void testConstructorAndDefaultValues() {
        LocalDate date = LocalDate.of(2025, 3, 1);
        RakutenOrder order = new RakutenOrder("R123", date);

        assertEquals("R123", order.getOrderId());
        assertEquals("RAKUTEN", order.getOrderSource());
        assertEquals(date, order.getOrderDate());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());
    }

    @Test
    void testRelationships() {
        RakutenOrder order = new RakutenOrder("R123", LocalDate.now());

        RakutenItem item = new RakutenItem("Shoes");
        order.addItem(item);
        assertTrue(order.getItems().contains(item));
        assertEquals(order, item.getOrder());

        RakutenPayment payment = new RakutenPayment("MasterCard");
        order.addPayment(payment);
        assertTrue(order.getPayments().contains(payment));
        assertEquals(order, payment.getOrder());

        RakutenShipment shipment = new RakutenShipment(LocalDate.now(), "JP Post", "87654321");
        order.addShipment(shipment);
        assertTrue(order.getShipments().contains(shipment));
        assertEquals(order, shipment.getOrder());
    }

    @Test
    void testMerge_OverwritesFactsPreservesReconciledAmountAndUserFields() {
        LocalDate orderDate = LocalDate.of(2025, 3, 1);
        RakutenOrder original = new RakutenOrder("R123", orderDate);
        original.setShippingDate(LocalDate.of(2025, 3, 2));
        original.setReconciledAmount(new BigDecimal("100"));

        // Item 1: Has user-managed category and privacy override
        RakutenItem existingItem1 = new RakutenItem("Old Item");
        existingItem1.setUnitPrice(new BigDecimal("500"));
        existingItem1.setQuantity(1);
        ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory cat =
                new ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory("Clothes", true, false, null);
        existingItem1.setCategory(cat);
        existingItem1.setPrivacyLevel(ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel.PRIVATE);
        original.addItem(existingItem1);

        // Incoming parsed order with updated shippingDate, facts, orderDate, taxAmount, and new item
        LocalDate updatedOrderDate = LocalDate.of(2025, 3, 4);
        RakutenOrder parsed = new RakutenOrder("R123", updatedOrderDate);
        parsed.setTotalAmount(new BigDecimal("1200"));
        parsed.setShippingDate(LocalDate.of(2025, 3, 6)); // Must overwrite
        parsed.setTaxAmount(new BigDecimal("120"));

        RakutenItem incomingItem1 = new RakutenItem("Old Item");
        incomingItem1.setUnitPrice(new BigDecimal("500"));
        incomingItem1.setQuantity(2);
        incomingItem1.setSeller("New Rakuten Shop");
        parsed.addItem(incomingItem1);

        RakutenItem incomingItem2 = new RakutenItem("New Item");
        incomingItem2.setUnitPrice(new BigDecimal("700"));
        parsed.addItem(incomingItem2);

        // Act
        original.merge(parsed);

        // Assert: Facts updated
        assertEquals(updatedOrderDate, original.getOrderDate(), "Order date must be overwritten");
        assertEquals(new BigDecimal("1200"), original.getTotalAmount());
        assertEquals(LocalDate.of(2025, 3, 6), original.getShippingDate(), "Shipping date must be overwritten");
        assertEquals(new BigDecimal("120"), original.getTaxAmount(), "Tax amount must be overwritten");
        assertEquals(new BigDecimal("100"), original.getReconciledAmount(), "Reconciled amount must be preserved");

        // Assert: Items merged loosely without deletion
        assertEquals(2, original.getItems().size());
        RakutenItem mergedItem1 = original.getItems().stream()
                .filter(i -> "Old Item".equals(i.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedItem1);
        assertEquals(2, mergedItem1.getQuantity());
        assertEquals("New Rakuten Shop", mergedItem1.getSeller());
        assertEquals(cat, mergedItem1.getCategory(), "User-assigned category must be strictly preserved");
        assertEquals(ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel.PRIVATE, mergedItem1.getPrivacyLevel(),
                "User-assigned privacyLevel must be strictly preserved");

        // Null incoming handled safely
        original.merge(null);
        assertEquals(new BigDecimal("1200"), original.getTotalAmount());
    }

    @Test
    void testNoArgsConstructor() {
        RakutenOrder order = new RakutenOrder();
        assertNotNull(order);
    }

    @Test
    void testTotalDeductions() {
        RakutenOrder order = new RakutenOrder("R123", LocalDate.now());
        order.setPointsUsed(new BigDecimal("100"));
        order.setDiscounts(new BigDecimal("50"));
        assertEquals(new BigDecimal("150"), order.getTotalDeductions());
    }
}
