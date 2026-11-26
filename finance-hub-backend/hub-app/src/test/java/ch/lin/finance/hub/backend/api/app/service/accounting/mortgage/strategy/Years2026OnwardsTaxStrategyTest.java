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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;

class Years2026OnwardsTaxStrategyTest {

    private Years2026OnwardsTaxStrategy strategy;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        strategy = new Years2026OnwardsTaxStrategy();
    }

    @Test
    void testIsApplicable() {
        assertFalse(strategy.isApplicable(LocalDate.of(2025, 12, 31)));
        assertTrue(strategy.isApplicable(LocalDate.of(2026, 1, 1)));
        assertTrue(strategy.isApplicable(LocalDate.of(2030, 12, 31)));
    }

    @Test
    void testCalculateRate() {
        assertEquals(0.007, strategy.calculateRate(ResidenceType.GENERAL));
        assertEquals(0.007, strategy.calculateRate(ResidenceType.ZEH));
    }

    @Test
    void testCalculateMaxAmount() {
        assertEquals(315_000L, strategy.calculateMaxAmount(ResidenceType.LONG_TERM_CERTIFIED));
        assertEquals(245_000L, strategy.calculateMaxAmount(ResidenceType.ZEH));
        assertEquals(140_000L, strategy.calculateMaxAmount(ResidenceType.ENERGY_SAVING));
        assertEquals(0L, strategy.calculateMaxAmount(ResidenceType.GENERAL));
        assertEquals(0L, strategy.calculateMaxAmount(null));
    }

    @Test
    void testCalculateDurationYears() {
        assertEquals(13, strategy.calculateDurationYears(PropertyType.NEW_DETACHED_HOUSE));
        assertEquals(13, strategy.calculateDurationYears(PropertyType.NEW_APARTMENT));
        assertEquals(10, strategy.calculateDurationYears(PropertyType.USED_DETACHED_HOUSE));
        assertEquals(10, strategy.calculateDurationYears(PropertyType.USED_APARTMENT));
        assertEquals(10, strategy.calculateDurationYears(null));
    }
}
