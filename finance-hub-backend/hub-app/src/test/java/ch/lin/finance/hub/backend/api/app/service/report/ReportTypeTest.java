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
package ch.lin.finance.hub.backend.api.app.service.report;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

class ReportTypeTest {

    @Test
    void testSourcesAssignment() {
        assertEquals(Set.of(TransactionSource.AMAZON, TransactionSource.CREDIT_CARD, TransactionSource.RAKUTEN),
                ReportType.ALL.getSources());
        assertEquals(Set.of(TransactionSource.AMAZON, TransactionSource.RAKUTEN), ReportType.SHOPPING.getSources());
        assertEquals(Set.of(TransactionSource.CREDIT_CARD), ReportType.CREDIT_CARD.getSources());
        assertEquals(Set.of(TransactionSource.AMAZON), ReportType.AMAZON.getSources());
        assertEquals(Set.of(TransactionSource.RAKUTEN), ReportType.RAKUTEN.getSources());
    }
}
