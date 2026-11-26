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
package ch.lin.finance.hub.backend.api.domain.model.profile.child.education;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;

class ChildEducationSettingsTest {

    @Test
    void testConstructorAndDefaultValues() {
        ChildEducationSettings settings = new ChildEducationSettings(1L);

        assertEquals(1L, settings.getChildProfileId());
        assertEquals(PreschoolTrack.HOME, settings.getPreschoolTrack());
        assertNull(settings.getPreschoolOverride());
        assertFalse(settings.getPreschoolSplitEnabled());
        assertNull(settings.getPreschoolStage1Duration());
        assertNull(settings.getPreschoolTrack2());
        assertNull(settings.getPreschoolOverride2());
        assertEquals(PrimaryTrack.PUBLIC, settings.getPrimaryTrack());
        assertEquals(MiddleTrack.PUBLIC, settings.getMiddleTrack());
        assertFalse(settings.getIsAwayFromHome());
    }

    @Test
    void testSettersAndGettersForPreschoolTwoStage() {
        ChildEducationSettings settings = new ChildEducationSettings(2L);
        StageOverride override2 = StageOverride.builder()
                .manualTuition(new BigDecimal("1800000"))
                .manualDuration(24)
                .build();

        settings.setPreschoolSplitEnabled(true);
        settings.setPreschoolStage1Duration(12);
        settings.setPreschoolTrack2(PreschoolTrack.INTL);
        settings.setPreschoolOverride2(override2);

        assertTrue(settings.getPreschoolSplitEnabled());
        assertEquals(12, settings.getPreschoolStage1Duration());
        assertEquals(PreschoolTrack.INTL, settings.getPreschoolTrack2());
        assertEquals(override2, settings.getPreschoolOverride2());
    }

    @Test
    void testBuilderConstructorWithTwoStage() {
        StageOverride override1 = StageOverride.builder().manualTuition(BigDecimal.ZERO).build();
        StageOverride override2 = StageOverride.builder().manualTuition(new BigDecimal("2000000")).build();

        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(10L)
                .preschoolTrack(PreschoolTrack.HOME)
                .preschoolOverride(override1)
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(12)
                .preschoolTrack2(PreschoolTrack.INTL)
                .preschoolOverride2(override2)
                .primaryTrack(PrimaryTrack.PUBLIC)
                .build();

        assertEquals(10L, settings.getChildProfileId());
        assertEquals(PreschoolTrack.HOME, settings.getPreschoolTrack());
        assertEquals(override1, settings.getPreschoolOverride());
        assertTrue(settings.getPreschoolSplitEnabled());
        assertEquals(12, settings.getPreschoolStage1Duration());
        assertEquals(PreschoolTrack.INTL, settings.getPreschoolTrack2());
        assertEquals(override2, settings.getPreschoolOverride2());
        assertEquals(PrimaryTrack.PUBLIC, settings.getPrimaryTrack());
    }

    @Test
    void testLegacyConstructorCompatibility() {
        ChildEducationSettings settings = new ChildEducationSettings(
                15L,
                PreschoolTrack.DAYCARE,
                null,
                PrimaryTrack.PUBLIC,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                false
        );

        assertEquals(15L, settings.getChildProfileId());
        assertEquals(PreschoolTrack.DAYCARE, settings.getPreschoolTrack());
        assertFalse(settings.getPreschoolSplitEnabled());
        assertNull(settings.getPreschoolStage1Duration());
        assertNull(settings.getPreschoolTrack2());
        assertNull(settings.getPreschoolOverride2());
    }
}
