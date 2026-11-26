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

import org.junit.jupiter.api.Test;

class ReportSortOptionTest {

    @Test
    void testEnumValues() {
        ReportSortOption[] options = ReportSortOption.values();
        assertEquals(4, options.length);
        assertEquals(ReportSortOption.DATE_DESC, ReportSortOption.valueOf("DATE_DESC"));
        assertEquals(ReportSortOption.DATE_ASC, ReportSortOption.valueOf("DATE_ASC"));
        assertEquals(ReportSortOption.AMOUNT_DESC, ReportSortOption.valueOf("AMOUNT_DESC"));
        assertEquals(ReportSortOption.AMOUNT_ASC, ReportSortOption.valueOf("AMOUNT_ASC"));
    }
}
