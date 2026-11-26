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
package ch.lin.finance.hub.backend.api.domain.model.accounting.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TransactionCategoryTest {

    @Test
    void testConstructorAndGetters() {
        TransactionCategory parent = new TransactionCategory("Food", true, false, null);
        TransactionCategory child = new TransactionCategory("Groceries", true, false, parent);

        assertEquals("Food", parent.getName());
        assertTrue(parent.isEssential());
        assertNull(parent.getParent());

        assertEquals("Groceries", child.getName());
        assertTrue(child.isEssential());
        assertEquals(parent, child.getParent());
    }

    @Test
    void testSetters() {
        TransactionCategory category = new TransactionCategory("Food", true, false, null);
        category.setName("Entertainment");
        category.setEssential(false);

        TransactionCategory newParent = new TransactionCategory("Fun", false, false, null);
        category.setParent(newParent);

        assertEquals("Entertainment", category.getName());
        assertFalse(category.isEssential());
        assertEquals(newParent, category.getParent());
    }
}
