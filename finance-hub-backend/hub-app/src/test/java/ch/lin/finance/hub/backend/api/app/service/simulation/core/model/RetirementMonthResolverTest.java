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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.model;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;

class RetirementMonthResolverTest {

    @Test
    void testResolveRetirementMonth_DecemberMode_ReturnsTwelveRegardlessOfBirthDate() {
        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 5, 20),
                RetirementMonthMode.DECEMBER));
        assertEquals(12,
                RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 1, 1), RetirementMonthMode.DECEMBER));
        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(null, RetirementMonthMode.DECEMBER));
    }

    @Test
    void testResolveRetirementMonth_BirthMonthMode_ReturnsBirthMonthValue() {
        assertEquals(1, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 1, 15),
                RetirementMonthMode.BIRTH_MONTH));
        assertEquals(6, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 6, 30),
                RetirementMonthMode.BIRTH_MONTH));
        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 12, 5),
                RetirementMonthMode.BIRTH_MONTH));
    }

    @Test
    void testResolveRetirementMonth_BirthMonthMode_NullBirthDate_ReturnsNull() {
        assertNull(RetirementMonthResolver.resolveRetirementMonth(null, RetirementMonthMode.BIRTH_MONTH));
    }

    @Test
    void testResolveRetirementMonth_NullMode_DefaultsToDecember() {
        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(LocalDate.of(1961, 7, 10), null));
        assertEquals(12, RetirementMonthResolver.resolveRetirementMonth(null, null));
    }
}
