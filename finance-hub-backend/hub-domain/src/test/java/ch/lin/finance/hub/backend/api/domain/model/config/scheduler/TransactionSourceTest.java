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
package ch.lin.finance.hub.backend.api.domain.model.config.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class TransactionSourceTest {

    @Test
    void testEnumValues() {
        assertNotNull(TransactionSource.valueOf("AMAZON"));
        assertNotNull(TransactionSource.valueOf("RAKUTEN"));
        assertNotNull(TransactionSource.valueOf("CREDIT_CARD"));
        assertNotNull(TransactionSource.valueOf("BANK"));
        assertNotNull(TransactionSource.valueOf("SALARY"));
        assertNotNull(TransactionSource.valueOf("NISA"));
        assertNotNull(TransactionSource.valueOf("COMPANY_STOCK"));
        assertNotNull(TransactionSource.valueOf("DEFINED_CONTRIBUTION"));
        assertNotNull(TransactionSource.valueOf("PENSION"));
        assertNotNull(TransactionSource.valueOf("INSURANCE"));
        assertNotNull(TransactionSource.valueOf("MORTGAGE"));
        assertEquals(12, TransactionSource.values().length);
    }
}
