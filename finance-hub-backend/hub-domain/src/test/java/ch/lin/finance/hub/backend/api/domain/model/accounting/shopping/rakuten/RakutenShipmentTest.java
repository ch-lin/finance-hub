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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RakutenShipmentTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate date = LocalDate.now();
        RakutenShipment shipment = new RakutenShipment(date, "JP Post", "12345");

        assertEquals(date, shipment.getShippingDate());
        assertEquals("JP Post", shipment.getCarrier());
        assertEquals("12345", shipment.getTrackingNumber());
    }

    @Test
    void testNoArgsConstructor() {
        RakutenShipment shipment = new RakutenShipment();
        assertNotNull(shipment);
    }
}
