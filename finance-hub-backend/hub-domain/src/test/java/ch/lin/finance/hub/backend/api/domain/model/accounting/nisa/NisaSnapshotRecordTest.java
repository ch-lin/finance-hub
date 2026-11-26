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
package ch.lin.finance.hub.backend.api.domain.model.accounting.nisa;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class NisaSnapshotRecordTest {

    @Test
    void testConstructorAndGetters() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        NisaSnapshotRecord record = new NisaSnapshotRecord(
                date,
                "NISA",
                "03311187",
                "S&P 500",
                100.0,
                150.0,
                200.0,
                15000L,
                20000L,
                5000L);

        assertEquals(date, record.getRecordDate());
        assertEquals("NISA", record.getAccountType());
        assertEquals("03311187", record.getFundCode());
        assertEquals("S&P 500", record.getFundName());
        assertEquals(100.0, record.getUnits());
        assertEquals(150.0, record.getAverageCost());
        assertEquals(200.0, record.getCurrentPrice());
        assertEquals(15000L, record.getTotalPrincipal());
        assertEquals(20000L, record.getCurrentMarketValue());
        assertEquals(5000L, record.getUnrealizedGainLoss());
    }

    @Test
    void testConstructorWithNulls() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        NisaSnapshotRecord record = new NisaSnapshotRecord(date, "NISA", null, "S&P 500", null, null, null, null, null,
                null);

        assertNull(record.getFundCode());
        assertEquals(0.0, record.getUnits());
        assertEquals(0.0, record.getAverageCost());
        assertEquals(0.0, record.getCurrentPrice());
        assertEquals(0L, record.getTotalPrincipal());
        assertEquals(0L, record.getCurrentMarketValue());
        assertEquals(0L, record.getUnrealizedGainLoss());
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDate date = LocalDate.of(2025, 1, 1);

        NisaSnapshotRecord r1 = new NisaSnapshotRecord(date, "NISA", "03311187", "S&P 500", null, null, null, null,
                null, null);
        NisaSnapshotRecord r2 = new NisaSnapshotRecord(date, "NISA", "03311187", "S&P 500 Renamed", null, null, null,
                null, null, null);
        NisaSnapshotRecord r3 = new NisaSnapshotRecord(date, "NISA2", "03311187", "S&P 500", null, null, null, null,
                null, null);
        NisaSnapshotRecord r4 = new NisaSnapshotRecord(date, "NISA", "0331418A", "S&P 500", null, null, null, null,
                null, null);

        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
        assertNotEquals(r1, r4);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1.hashCode(), r3.hashCode());
        assertNotEquals(r1.hashCode(), r4.hashCode());
    }

    @Test
    void testMerge_OverwritesFactsAndPreservesExpectedReturnRate() {
        LocalDate date1 = LocalDate.of(2025, 1, 1);
        NisaSnapshotRecord existing = new NisaSnapshotRecord(
                date1, "NISA (成長)", "03311187", "Old Fund", 100.0, 150.0, 200.0, 15000L, 20000L, 5000L);
        existing.updateExpectedReturnRate(0.07);

        LocalDate date2 = LocalDate.of(2025, 1, 2);
        NisaSnapshotRecord incoming = new NisaSnapshotRecord(
                date2, "NISA (成長)", "03311188", "Updated Fund", 120.0, 160.0, 220.0, 19200L, 26400L, 7200L);

        existing.merge(incoming);

        // Factual fields overwritten
        assertEquals(date2, existing.getRecordDate());
        assertEquals("NISA (成長)", existing.getAccountType());
        assertEquals("03311188", existing.getFundCode());
        assertEquals("Updated Fund", existing.getFundName());
        assertEquals(120.0, existing.getUnits());
        assertEquals(160.0, existing.getAverageCost());
        assertEquals(220.0, existing.getCurrentPrice());
        assertEquals(19200L, existing.getTotalPrincipal());
        assertEquals(26400L, existing.getCurrentMarketValue());
        assertEquals(7200L, existing.getUnrealizedGainLoss());

        // User-defined expectedReturnRate strictly preserved
        assertEquals(0.07, existing.getExpectedReturnRate());

        // Null safe
        existing.merge(null);
        assertEquals(120.0, existing.getUnits());
        assertEquals(0.07, existing.getExpectedReturnRate());
    }
}
