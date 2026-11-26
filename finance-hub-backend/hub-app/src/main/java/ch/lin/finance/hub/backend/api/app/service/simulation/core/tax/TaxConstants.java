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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;

/**
 * Constants related to tax calculations, limits, and financial rules used in
 * simulations.
 * These values often represent statutory limits or default rates in the
 * Japanese tax system.
 */
public final class TaxConstants {

    private TaxConstants() {
        // Prevent instantiation
    }

    /** Maximum lifetime contribution limit for NISA (New NISA from 2024). */
    public static final long NISA_LIFETIME_LIMIT = 18_000_000L;

    /** Maximum annual contribution limit for NISA (Growth + Tsumitate combined). */
    public static final long NISA_ANNUAL_LIMIT = 3_600_000L;

    /**
     * Annual sub-limit for the 成長投資枠 (Growth) quota.
     * Non-fungible with the tsumitate quota — excess is NOT automatically redirected.
     */
    public static final long NISA_GROWTH_ANNUAL_LIMIT = 2_400_000L;

    /**
     * Lifetime sub-limit for the 成長投資枠 (Growth) quota.
     * The tsumitate quota has no independent lifetime cap; it is bounded only by the
     * combined NISA_LIFETIME_LIMIT minus accumulated growth principal.
     */
    public static final long NISA_GROWTH_LIFETIME_LIMIT = 12_000_000L;

    /**
     * Annual sub-limit for the つみたて投資枠 (Tsumitate) quota.
     * Non-fungible with the growth quota.
     */
    public static final long NISA_TSUMITATE_ANNUAL_LIMIT = 1_200_000L;

    /** Standard monthly premium for Kokumin Nenkin (National Pension). */
    public static final long KOKUMIN_NENKIN_MONTHLY_PREMIUM = 16_980L;

    /**
     * Interest rate applied when calculating cost of negative cashflow (e.g.,
     * borrowing rate).
     */
    public static final double NEGATIVE_CASH_BORROWING_RATE = 0.14;

    /** Default fallback health insurance rate (~4.9% employee portion). */
    public static final double DEFAULT_HEALTH_INSURANCE_RATE = 0.049;

    /** Default fallback pension rate (~9.15% employee portion). */
    public static final double DEFAULT_PENSION_RATE = 0.0915;

    /** Default fallback employment insurance rate (~0.6% employee portion). */
    public static final double DEFAULT_EMPLOYMENT_INSURANCE_RATE = 0.006;

    /** Monthly salary cap for health insurance premium calculation. */
    public static final long HEALTH_INSURANCE_MONTHLY_CAP = 1_390_000L;

    /**
     * Simplified annual health insurance premium for early retirees or non-working
     * individuals (under 75).
     */
    public static final long RETIREE_HEALTH_INSURANCE_PREMIUM = 400_000L;
}
