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
package ch.lin.finance.hub.backend.api.app.util;

import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Utility class for handling privacy masking logic.
 */
public class PrivacyMaskingUtil {

    private PrivacyMaskingUtil() {
        // Utility class
    }

    @Getter
    @RequiredArgsConstructor
    public enum MaskingType {
        BANK("Bank Transaction"),
        CREDIT_CARD("Credit Card Transaction"),
        AMAZON("Amazon Shopping"),
        RAKUTEN("Rakuten Shopping"),
        SOFMAP("Sofmap Shopping"),
        SALARY("Salary Record"),
        MORTGAGE("Mortgage Record"),
        PENSION("Pension Record"),
        NISA("NISA Asset"),
        DC("Defined Contribution Asset"),
        COMPANY_STOCK("Company Stock"),
        LIFE_EVENT("Life Event"),
        CAREER_EVENT("Career Event"),
        CATEGORY("Transaction Category");

        private final String defaultMaskedName;
    }

    /**
     * Determines whether an item needs to be masked based on its privacy level
     * and the current user. Use this method when evaluating a single entity
     * (e.g., BankTransaction, CreditCardTransaction).
     *
     * @param currentUserId The ID of the user requesting the data.
     * @param ownerUserId The ID of the user who owns the data.
     * @param entityLevel The privacy level of the entity.
     * @return true if masking is required, false otherwise.
     */
    public static boolean shouldMask(String currentUserId, String ownerUserId, PrivacyLevel entityLevel) {
        // 1. Only when the current user is verified as the owner, never mask it.
        if (currentUserId != null && currentUserId.equals(ownerUserId)) {
            return false;
        }

        // 2. In all other cases (non-owners, unauthenticated users, or family members):
        // Only explicitly SHARED entities are visible; all others (AGGREGATE_ONLY, PRIVATE, null) are masked.
        return entityLevel != PrivacyLevel.SHARED;
    }

    /**
     * Determines whether an item needs to be masked based on its privacy level,
     * falling back to parent's privacy level. Use this method when evaluating a
     * child item (e.g., AmazonItem) that can inherit privacy from its parent.
     *
     * @param currentUserId The ID of the user requesting the data.
     * @param ownerUserId The ID of the user who owns the data.
     * @param itemPrivacyLevel The privacy level of the item itself (can be
     * null).
     * @param parentPrivacyLevel The privacy level of the parent (fallback).
     * @return true if masking is required, false otherwise.
     */
    public static boolean shouldMask(String currentUserId, String ownerUserId, PrivacyLevel itemPrivacyLevel, PrivacyLevel parentPrivacyLevel) {
        PrivacyLevel effectiveLevel = (itemPrivacyLevel != null) ? itemPrivacyLevel : parentPrivacyLevel;
        return shouldMask(currentUserId, ownerUserId, effectiveLevel);
    }

    /**
     * Gets the standard masked name for a given source type.
     *
     * @param type The type of source.
     * @return The standard masked name in English.
     */
    public static String getMaskedName(MaskingType type) {
        if (type == null) {
            return "Masked Data";
        }
        return type.getDefaultMaskedName();
    }
}
