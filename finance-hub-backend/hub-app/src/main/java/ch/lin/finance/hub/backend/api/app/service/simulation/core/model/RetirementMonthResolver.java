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

import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;

/**
 * Utility to resolve the retirement month (the last active working month)
 * for a user during their target retirement year.
 */
public final class RetirementMonthResolver {

    private RetirementMonthResolver() {
        // Utility class
    }

    /**
     * Resolves the last working month (1-12) of employment for the retirement year.
     * <p>
     * - Under {@link RetirementMonthMode#DECEMBER} (or if mode is
     * null/unspecified),
     * returns 12 regardless of whether birthDate is present (the user works through
     * the end of December).
     * - Under {@link RetirementMonthMode#BIRTH_MONTH}, returns the month of
     * {@code birthDate} (1-12),
     * or {@code null} if {@code birthDate} is unavailable.
     *
     * @param birthDate the user's birth date, or null if unknown
     * @param mode      the retirement month mode configured, or null for default
     *                  DECEMBER
     * @return the last calendar month of working (1-12), or null if birthDate is
     *         required but missing
     */
    public static Integer resolveRetirementMonth(LocalDate birthDate, RetirementMonthMode mode) {
        if (mode == RetirementMonthMode.BIRTH_MONTH) {
            return birthDate != null ? birthDate.getMonthValue() : null;
        }
        return 12; // DECEMBER mode (default)
    }
}
