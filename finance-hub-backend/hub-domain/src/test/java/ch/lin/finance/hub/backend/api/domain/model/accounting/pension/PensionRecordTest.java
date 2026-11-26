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
package ch.lin.finance.hub.backend.api.domain.model.accounting.pension;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PensionRecordTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        PensionRecord record = new PensionRecord(
                date,
                700000L,
                800000L,
                1500000L,
                12,
                24,
                36,
                48,
                60,
                96,
                120,
                1000000L,
                2000000L,
                3000000L
        );

        assertEquals(date, record.getRecordDate());
        assertEquals(700000L, record.getBasicPensionAmount());
        assertEquals(800000L, record.getKouseiPensionAmount());
        assertEquals(1500000L, record.getTotalPensionAmount());
        assertEquals(12, record.getKokuminCategory1Months());
        assertEquals(24, record.getKokuminCategory3Months());
        assertEquals(36, record.getKokuminTotalMonths());
        assertEquals(48, record.getKouseiGeneralMonths());
        assertEquals(60, record.getKouseiTotalMonths());
        assertEquals(96, record.getTotalEnrolledMonths());
        assertEquals(120, record.getQualifyingMonths());
        assertEquals(1000000L, record.getKokuminPremiumPaid());
        assertEquals(2000000L, record.getKouseiPremiumPaid());
        assertEquals(3000000L, record.getTotalPremiumPaid());
    }

    @Test
    void testConstructorWithNulls() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        PensionRecord record = new PensionRecord(date, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(0L, record.getBasicPensionAmount());
        assertEquals(0L, record.getKouseiPensionAmount());
        assertEquals(0L, record.getTotalPensionAmount());
        assertEquals(0, record.getKokuminCategory1Months());
        assertEquals(0, record.getKokuminCategory3Months());
        assertEquals(0, record.getKokuminTotalMonths());
        assertEquals(0, record.getKouseiGeneralMonths());
        assertEquals(0, record.getKouseiTotalMonths());
        assertEquals(0, record.getTotalEnrolledMonths());
        assertEquals(0, record.getQualifyingMonths());
        assertEquals(0L, record.getKokuminPremiumPaid());
        assertEquals(0L, record.getKouseiPremiumPaid());
        assertEquals(0L, record.getTotalPremiumPaid());
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDate date1 = LocalDate.of(2025, 1, 1);
        LocalDate date2 = LocalDate.of(2025, 2, 1);
        
        PensionRecord r1 = new PensionRecord(date1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        PensionRecord r2 = new PensionRecord(date1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        PensionRecord r3 = new PensionRecord(date2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        
        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1.hashCode(), r3.hashCode());
    }
}
