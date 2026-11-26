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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil.MaskingType;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;

class PrivacyMaskingUtilTest {

    private static final String OWNER_ID = "owner123";
    private static final String OTHER_USER_ID = "other456";

    @Test
    void shouldMask_WhenUserIsOwner_ShouldReturnFalse() {
        assertFalse(PrivacyMaskingUtil.shouldMask(OWNER_ID, OWNER_ID, PrivacyLevel.PRIVATE));
        assertFalse(PrivacyMaskingUtil.shouldMask(OWNER_ID, OWNER_ID, PrivacyLevel.AGGREGATE_ONLY));
        assertFalse(PrivacyMaskingUtil.shouldMask(OWNER_ID, OWNER_ID, PrivacyLevel.SHARED));
    }

    @Test
    void shouldMask_WhenUserIsNotOwner_ShouldMaskAggregateOnlyAndPrivate() {
        assertTrue(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, PrivacyLevel.PRIVATE));
        assertTrue(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, PrivacyLevel.AGGREGATE_ONLY));
        assertFalse(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, PrivacyLevel.SHARED));
    }

    @Test
    void shouldMask_WithFallback_WhenItemLevelIsNotNull_ShouldUseItemLevel() {
        // Item is SHARED, Parent is PRIVATE -> Effective is SHARED -> Should NOT mask
        assertFalse(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, PrivacyLevel.SHARED, PrivacyLevel.PRIVATE));

        // Item is PRIVATE, Parent is SHARED -> Effective is PRIVATE -> Should mask
        assertTrue(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, PrivacyLevel.PRIVATE, PrivacyLevel.SHARED));
    }

    @Test
    void shouldMask_WithFallback_WhenItemLevelIsNull_ShouldUseParentLevel() {
        // Item is null, Parent is SHARED -> Effective is SHARED -> Should NOT mask
        assertFalse(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, null, PrivacyLevel.SHARED));

        // Item is null, Parent is AGGREGATE_ONLY -> Effective is AGGREGATE_ONLY ->
        // Should mask
        assertTrue(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, null, PrivacyLevel.AGGREGATE_ONLY));
    }

    @Test
    void shouldMask_WhenNullUserIds_FailClosed_ShouldMaskIfPrivateOrAggregate() {
        assertTrue(PrivacyMaskingUtil.shouldMask(null, OWNER_ID, PrivacyLevel.PRIVATE));
        assertTrue(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, null, PrivacyLevel.PRIVATE));
        assertFalse(PrivacyMaskingUtil.shouldMask(null, OWNER_ID, PrivacyLevel.SHARED));
    }

    @Test
    void shouldMask_WithFallback_WhenBothLevelsAreNull_FailClosed_ShouldMask() {
        assertTrue(PrivacyMaskingUtil.shouldMask(OTHER_USER_ID, OWNER_ID, null, null));
    }

    @Test
    void getMaskedName_ShouldReturnCorrectNames() {
        assertEquals("Bank Transaction", PrivacyMaskingUtil.getMaskedName(MaskingType.BANK));
        assertEquals("Amazon Shopping", PrivacyMaskingUtil.getMaskedName(MaskingType.AMAZON));
        assertEquals("Rakuten Shopping", PrivacyMaskingUtil.getMaskedName(MaskingType.RAKUTEN));
        assertEquals("Sofmap Shopping", PrivacyMaskingUtil.getMaskedName(MaskingType.SOFMAP));
        assertEquals("Credit Card Transaction", PrivacyMaskingUtil.getMaskedName(MaskingType.CREDIT_CARD));
        assertEquals("Salary Record", PrivacyMaskingUtil.getMaskedName(MaskingType.SALARY));
        assertEquals("Mortgage Record", PrivacyMaskingUtil.getMaskedName(MaskingType.MORTGAGE));
        assertEquals("Pension Record", PrivacyMaskingUtil.getMaskedName(MaskingType.PENSION));
        assertEquals("NISA Asset", PrivacyMaskingUtil.getMaskedName(MaskingType.NISA));
        assertEquals("Defined Contribution Asset", PrivacyMaskingUtil.getMaskedName(MaskingType.DC));
        assertEquals("Company Stock", PrivacyMaskingUtil.getMaskedName(MaskingType.COMPANY_STOCK));
        assertEquals("Life Event", PrivacyMaskingUtil.getMaskedName(MaskingType.LIFE_EVENT));
        assertEquals("Career Event", PrivacyMaskingUtil.getMaskedName(MaskingType.CAREER_EVENT));
        assertEquals("Transaction Category", PrivacyMaskingUtil.getMaskedName(MaskingType.CATEGORY));
        assertEquals("Masked Data", PrivacyMaskingUtil.getMaskedName(null));
    }
}
