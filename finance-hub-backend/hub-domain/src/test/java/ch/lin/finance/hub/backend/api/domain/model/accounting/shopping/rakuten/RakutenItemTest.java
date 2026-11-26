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

import static org.junit.jupiter.api.Assertions.*;

class RakutenItemTest {

    @Test
    void testConstructorAndGetters() {
        RakutenItem item = new RakutenItem("Monitor");
        assertEquals("Monitor", item.getName());
        assertEquals(1, item.getQuantity());
        assertEquals(BigDecimal.ZERO, item.getUnitPrice());
    }

    @Test
    void testNoArgsConstructor() {
        RakutenItem item = new RakutenItem();
        assertNotNull(item);
    }
}
