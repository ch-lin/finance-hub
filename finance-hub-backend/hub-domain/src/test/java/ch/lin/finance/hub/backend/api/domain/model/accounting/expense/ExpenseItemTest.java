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
package ch.lin.finance.hub.backend.api.domain.model.accounting.expense;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseItemTest {

    @Test
    void testGettersAndReflectionInstatiation() throws Exception {
        ExpenseItem item = new ExpenseItem();
        
        setField(item, "id", "AMAZON_1");
        setField(item, "source", "AMAZON");
        setField(item, "name", "Test Item");
        setField(item, "unitPrice", new BigDecimal("1000"));
        setField(item, "quantity", 2);
        setField(item, "categoryName", "Groceries");
        setField(item, "isEssential", true);
        
        LocalDate date = LocalDate.of(2025, 1, 1);
        setField(item, "orderDate", date);
        
        assertEquals("AMAZON_1", item.getId());
        assertEquals("AMAZON", item.getSource());
        assertEquals("Test Item", item.getName());
        assertEquals(new BigDecimal("1000"), item.getUnitPrice());
        assertEquals(2, item.getQuantity());
        assertEquals("Groceries", item.getCategoryName());
        assertTrue(item.getIsEssential());
        assertEquals(date, item.getOrderDate());
    }
    
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
