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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SofmapShipmentTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate date = LocalDate.of(2025, 3, 1);
        SofmapShipment shipment = new SofmapShipment(date, "Yamato", "1234", "出荷完了");
        assertEquals(date, shipment.getShippingDate());
        assertEquals("Yamato", shipment.getCarrier());
        assertEquals("1234", shipment.getTrackingNumber());
        assertEquals("出荷完了", shipment.getStatus());
    }

    @Test
    void testNoArgsConstructor() {
        SofmapShipment shipment = new SofmapShipment();
        assertNotNull(shipment);
    }
}
