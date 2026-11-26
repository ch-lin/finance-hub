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

/**
 * Discriminates between the two non-fungible NISA account sub-quota types
 * introduced by the 2024 New NISA reform.
 *
 * <p>
 * Annual contribution sub-limits (non-transferable between types):
 * <ul>
 * <li>{@link #GROWTH} — 成長投資枠: ¥2,400,000/yr, ¥12,000,000 lifetime</li>
 * <li>{@link #TSUMITATE} — つみたて投資枠: ¥1,200,000/yr</li>
 * </ul>
 * Combined annual limit: ¥3,600,000. Combined lifetime limit: ¥18,000,000.
 */
public enum NisaAccountType {

    /**
     * 成長投資枠 — growth/lump-sum quota. Annual cap ¥2,400,000, lifetime cap
     * ¥12,000,000.
     */
    GROWTH,

    /** つみたて投資枠 — accumulation/dollar-cost-average quota. Annual cap ¥1,200,000. */
    TSUMITATE;

    /**
     * Parses the raw account-type string scraped from an SBI Securities HTML
     * snapshot (e.g. {@code "NISA (成長)預り"} or {@code "NISA (つみたて)預り"})
     * into the corresponding enum constant.
     *
     * <p>
     * Defaults to {@link #GROWTH} when the account type recognition fails.
     *
     * <p>
     * This default is conservative only in terms of the "lifetime quota":
     * GROWTH's own lifetime sub-limit (12M JPY) is smaller than TSUMITATE's,
     * making it less likely to exceed the lifetime total limit when misclassified.
     *
     * <p>
     * However, in terms of "annual contribution limit", GROWTH is actually looser,
     * not more conservative: GROWTH's annual cap (2.4M JPY/yr) is twice that of
     * TSUMITATE's (1.2M JPY/yr) (see {@code TaxConstants.NISA_GROWTH_ANNUAL_LIMIT}
     * / {@code NISA_TSUMITATE_ANNUAL_LIMIT}). A fund that should be TSUMITATE but
     * misclassified as GROWTH will cause the annual contribution portion between
     * 1.2M and 2.4M to be falsely evaluated as a valid investment.
     *
     * <p>
     * This is a deliberate, known trade-off, not a bug — there is no need to
     * change this default behavior or apply a "stricter of the two" logic.
     *
     * @param rawAccountType the raw string from
     *                       {@code NisaSnapshotRecord.accountType}
     * @return the matching {@link NisaAccountType}
     */
    public static NisaAccountType parse(String rawAccountType) {
        if (rawAccountType == null || rawAccountType.isBlank()) {
            return GROWTH;
        }
        if (rawAccountType.contains("つみたて")) {
            return TSUMITATE;
        }
        // "NISA (成長)預り", "成長", "growth" (case-insensitive fallback)
        return GROWTH;
    }
}
