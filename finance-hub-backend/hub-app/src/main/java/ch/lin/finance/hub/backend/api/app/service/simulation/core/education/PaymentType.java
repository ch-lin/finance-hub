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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.education;

/**
 * Types of educational payment events.
 */
public enum PaymentType {
    /** Periodic tuition payment based on termsPerYear. */
    TUITION,
    /** One-time entrance fee charged at the absolute start of a stage. */
    ENTRANCE_FEE,
    /** Cram school / extracurricular coaching costs (middle & high school). */
    JUKU_COST,
    /** Study abroad living and accommodation costs (university). */
    LIVING_COST
}
