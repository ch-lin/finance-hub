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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten;

import java.io.File;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;

public class RakutenOrderParserDemoTest {
    @Test
    public void testParse() throws Exception {
        RakutenOrderParserImpl parser = new RakutenOrderParserImpl();
        File f = new File(getClass().getResource("/mock-data/shopping/rakuten/order.html").getFile());
        RakutenOrder order = parser.parse(f);
        
        assertEquals("213310-20170522-0647383360", order.getOrderId());
        assertEquals(new BigDecimal("10000"), order.getBilledAmount());
        assertEquals(new BigDecimal("10000"), order.getTotalAmount());
        
        assertEquals(1, order.getItems().size());
        RakutenItem item = order.getItems().get(0);
        assertEquals("Test Rakuten Item", item.getName());
        assertEquals(new BigDecimal("5000"), item.getUnitPrice());
        assertEquals(2, item.getQuantity());
    }
}
