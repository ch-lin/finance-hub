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
package ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SofmapOrderTest {

    @Test
    void testConstructorAndDefaultValues() {
        LocalDate date = LocalDate.of(2025, 3, 1);
        SofmapOrder order = new SofmapOrder("S123", date);

        assertEquals("S123", order.getOrderId());
        assertEquals("SOFMAP", order.getOrderSource());
        assertEquals(date, order.getOrderDate());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());
        assertEquals(BigDecimal.ZERO, order.getEarnedPoints());
    }

    @Test
    void testRelationships() {
        SofmapOrder order = new SofmapOrder("S123", LocalDate.now());

        SofmapItem item = new SofmapItem("Game");
        order.addItem(item);
        assertTrue(order.getItems().contains(item));
        assertEquals(order, item.getOrder());

        SofmapPayment payment = new SofmapPayment("Credit Card");
        order.addPayment(payment);
        assertTrue(order.getPayments().contains(payment));
        assertEquals(order, payment.getOrder());

        SofmapShipment shipment = new SofmapShipment(LocalDate.now(), "Yamato", "1234", "出荷完了");
        order.addShipment(shipment);
        assertTrue(order.getShipments().contains(shipment));
        assertEquals(order, shipment.getOrder());
    }

    @Test
    void testTotalDeductions() {
        SofmapOrder order = new SofmapOrder("S123", LocalDate.now());
        order.setPointsUsed(new BigDecimal("100"));
        order.setDiscounts(new BigDecimal("10"));

        assertEquals(new BigDecimal("110"), order.getTotalDeductions());
    }

    @Test
    void testUnreconciledBalance() {
        SofmapOrder order = new SofmapOrder("S123", LocalDate.now());
        order.setBilledAmount(new BigDecimal("1000"));
        order.setReconciledAmount(new BigDecimal("400"));

        assertEquals(new BigDecimal("600"), order.getUnreconciledBalance());
    }

    @Test
    void testMerge_OverwritesFactsPreservesReconciledAmountAndUserFields() {
        LocalDate orderDate = LocalDate.of(2025, 3, 1);
        SofmapOrder original = new SofmapOrder("S123", orderDate);
        original.setShippingDate(LocalDate.of(2025, 3, 2));
        original.setReconciledAmount(new BigDecimal("200"));

        // Item 1: Has user-managed category and privacy override
        SofmapItem existingItem1 = new SofmapItem("Old Item");
        existingItem1.setUnitPrice(new BigDecimal("1000"));
        existingItem1.setQuantity(1);
        ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory cat =
                new ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory("Electronics", true, false, null);
        existingItem1.setCategory(cat);
        existingItem1.setPrivacyLevel(ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel.PRIVATE);
        original.addItem(existingItem1);

        // Incoming parsed order with updated shippingDate, facts, earnedPoints, orderDate, and new item
        LocalDate updatedOrderDate = LocalDate.of(2025, 3, 5);
        SofmapOrder parsed = new SofmapOrder("S123", updatedOrderDate);
        parsed.setTotalAmount(new BigDecimal("2500"));
        parsed.setEarnedPoints(new BigDecimal("222"));
        parsed.setShippingDate(LocalDate.of(2025, 3, 7)); // Must overwrite

        SofmapItem incomingItem1 = new SofmapItem("Old Item");
        incomingItem1.setUnitPrice(new BigDecimal("1000"));
        incomingItem1.setQuantity(2);
        incomingItem1.setSeller("Sofmap Akiba");
        parsed.addItem(incomingItem1);

        SofmapItem incomingItem2 = new SofmapItem("New Item");
        incomingItem2.setUnitPrice(new BigDecimal("1500"));
        parsed.addItem(incomingItem2);

        // Act
        original.merge(parsed);

        // Assert: Facts updated
        assertEquals(updatedOrderDate, original.getOrderDate(), "Order date must be overwritten");
        assertEquals(new BigDecimal("2500"), original.getTotalAmount());
        assertEquals(new BigDecimal("222"), original.getEarnedPoints());
        assertEquals(LocalDate.of(2025, 3, 7), original.getShippingDate(), "Shipping date must be overwritten");
        assertEquals(new BigDecimal("200"), original.getReconciledAmount(), "Reconciled amount must be preserved");

        // Assert: Items merged loosely without deletion
        assertEquals(2, original.getItems().size());
        SofmapItem mergedItem1 = original.getItems().stream()
                .filter(i -> "Old Item".equals(i.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedItem1);
        assertEquals(2, mergedItem1.getQuantity());
        assertEquals("Sofmap Akiba", mergedItem1.getSeller());
        assertEquals(cat, mergedItem1.getCategory(), "User-assigned category must be strictly preserved");
        assertEquals(ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel.PRIVATE, mergedItem1.getPrivacyLevel(),
                "User-assigned privacyLevel must be strictly preserved");

        // Null incoming handled safely
        original.merge(null);
        assertEquals(new BigDecimal("2500"), original.getTotalAmount());
    }

    @Test
    void testNoArgsConstructor() {
        SofmapOrder order = new SofmapOrder();
        assertNotNull(order);
    }
}
