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
package ch.lin.finance.hub.backend.api.app.service.profile.child.education;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.education.ChildEducationSettingsRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.profile.child.education.command.StageOverrideCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.education.command.UpdateChildEducationSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.education.result.ChildEducationSettingsResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationAbsoluteTimeline;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationProfileResolver;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationTimelineProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.ResolvedEducationProfile;
import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineException;
import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.StageOverride;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ChildEducationSettingsServiceImplTest {

    @Mock
    private ChildEducationSettingsRepository settingsRepository;

    @Mock
    private ChildProfileRepository childProfileRepository;

    @Mock
    private EducationConfigService educationConfigService;

    @Mock
    private EducationProfileResolver educationProfileResolver;

    @Mock
    private EducationTimelineProjector educationTimelineProjector;

    @Mock
    private OwnershipGuard ownershipGuard;

    @InjectMocks
    private ChildEducationSettingsServiceImpl service;

    private ChildProfile childProfile;

    @Mock
    private EducationConfig defaultConfig;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        childProfile = ChildProfile.builder()
                .id(1L)
                .name("Taro")
                .birthDate(LocalDate.of(2020, 5, 10))
                .ownerUserId("42")
                .build();
    }

    @Test
    void getSettings_WhenExisting_ReturnsResult() {
        ChildEducationSettings settings = new ChildEducationSettings(1L);
        settings.setPrimaryTrack(PrimaryTrack.PUBLIC);

        when(ownershipGuard.loadReadable(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(settings));

        ChildEducationSettingsResult result = service.getSettings(1L);

        assertNotNull(result);
        assertEquals(PrimaryTrack.PUBLIC, result.getPrimaryTrack());
        assertEquals("42", result.getOwnerUserId());
    }

    @Test
    void updateSettings_WhenValid_ValidatesAndSaves() {
        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .build();

        ChildEducationSettings savedSettings = new ChildEducationSettings(1L);
        savedSettings.setPrimaryTrack(PrimaryTrack.PUBLIC);

        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());
        EducationAbsoluteTimeline validTimeline = EducationAbsoluteTimeline.builder()
                .events(Collections.emptyList())
                .build();

        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.empty());
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);
        when(educationTimelineProjector.project(any(), any())).thenReturn(validTimeline);
        when(settingsRepository.save(any())).thenReturn(savedSettings);

        ChildEducationSettingsResult result = service.updateSettings(1L, command);

        assertNotNull(result);
        assertEquals(PrimaryTrack.PUBLIC, result.getPrimaryTrack());
        verify(settingsRepository).save(any());
    }

    @Test
    void updateSettings_WhenTimelineExceedsMaxYears_ThrowsInvalidEducationTimelineException() {
        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .build();

        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());

        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.empty());
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);

        when(educationTimelineProjector.project(any(), any()))
                .thenThrow(new IllegalArgumentException("Negative or invalid stage interval"));

        InvalidEducationTimelineException ex = assertThrows(
                InvalidEducationTimelineException.class,
                () -> service.updateSettings(1L, command));
        assertNotNull(ex.getMessage());
    }

    @Test
    void updateSettings_WhenTrackIsNone_ClearsOverridesAndExtraCosts() {
        // Pre-existing settings with overrides and extra costs populated
        ChildEducationSettings existingSettings = new ChildEducationSettings(1L);
        existingSettings.setHighTrack(HighTrack.PUBLIC);
        existingSettings.setHighOverride(StageOverride.builder().manualDuration(36).build());
        existingSettings.setManualLivingCost(BigDecimal.valueOf(1200000));
        existingSettings.setIsAwayFromHome(true);

        // Command setting tracks to NONE
        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .highTrack(HighTrack.NONE)
                .highOverride(StageOverrideCommand.builder().manualDuration(36).build())
                .universityTrack(UniversityTrack.NONE)
                .universityOverride(StageOverrideCommand.builder().manualDuration(48).build())
                .manualLivingCost(BigDecimal.valueOf(1200000))
                .isAwayFromHome(true)
                .build();

        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());
        EducationAbsoluteTimeline validTimeline = EducationAbsoluteTimeline.builder()
                .events(Collections.emptyList())
                .build();

        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(existingSettings));
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);
        when(educationTimelineProjector.project(any(), any())).thenReturn(validTimeline);
        when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChildEducationSettingsResult result = service.updateSettings(1L, command);

        assertNotNull(result);
        ArgumentCaptor<ChildEducationSettings> captor = ArgumentCaptor.forClass(ChildEducationSettings.class);
        verify(settingsRepository).save(captor.capture());
        ChildEducationSettings saved = captor.getValue();

        assertEquals(HighTrack.NONE, saved.getHighTrack());
        assertNull(saved.getHighOverride());

        assertEquals(UniversityTrack.NONE, saved.getUniversityTrack());
        assertNull(saved.getUniversityOverride());
        assertNull(saved.getManualLivingCost());
        assertFalse(Boolean.TRUE.equals(saved.getIsAwayFromHome()));
    }

    @Test
    void updateSettings_WhenPreschoolSplitEnabled_SavesTwoStageFields() {
        ChildEducationSettings existing = new ChildEducationSettings(1L);

        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .preschoolTrack(PreschoolTrack.HOME)
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(12)
                .preschoolTrack2(PreschoolTrack.INTL)
                .preschoolOverride2(StageOverrideCommand.builder().manualTuition(new BigDecimal("1800000")).build())
                .build();

        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());
        EducationAbsoluteTimeline validTimeline = EducationAbsoluteTimeline.builder()
                .events(Collections.emptyList())
                .build();

        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(existing));
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);
        when(educationTimelineProjector.project(any(), any())).thenReturn(validTimeline);
        when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChildEducationSettingsResult result = service.updateSettings(1L, command);

        assertNotNull(result);
        assertTrue(result.getPreschoolSplitEnabled());
        assertEquals(12, result.getPreschoolStage1Duration());
        assertEquals(PreschoolTrack.INTL, result.getPreschoolTrack2());
        assertNotNull(result.getPreschoolOverride2());
        assertEquals(new BigDecimal("1800000"), result.getPreschoolOverride2().getManualTuition());

        ArgumentCaptor<ChildEducationSettings> captor = ArgumentCaptor.forClass(ChildEducationSettings.class);
        verify(settingsRepository).save(captor.capture());
        ChildEducationSettings saved = captor.getValue();
        assertTrue(saved.getPreschoolSplitEnabled());
        assertEquals(12, saved.getPreschoolStage1Duration());
        assertEquals(PreschoolTrack.INTL, saved.getPreschoolTrack2());
        assertNotNull(saved.getPreschoolOverride2());
    }

    @Test
    void updateSettings_WhenPreschoolSplitEnabled_ClearsManualDurationOnBothOverrides() {
        ChildEducationSettings existing = new ChildEducationSettings(1L);

        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .preschoolTrack(PreschoolTrack.DAYCARE)
                .preschoolOverride(StageOverrideCommand.builder()
                        .manualDuration(48)
                        .manualTuition(new BigDecimal("500000"))
                        .build())
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(12)
                .preschoolTrack2(PreschoolTrack.INTL)
                .preschoolOverride2(StageOverrideCommand.builder()
                        .manualDuration(24)
                        .manualTuition(new BigDecimal("1800000"))
                        .build())
                .build();

        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());
        EducationAbsoluteTimeline validTimeline = EducationAbsoluteTimeline.builder()
                .events(Collections.emptyList())
                .build();

        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(existing));
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);
        when(educationTimelineProjector.project(any(), any())).thenReturn(validTimeline);
        when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChildEducationSettingsResult result = service.updateSettings(1L, command);

        assertNotNull(result);
        assertTrue(result.getPreschoolSplitEnabled());
        assertNotNull(result.getPreschoolOverride());
        assertNull(result.getPreschoolOverride().getManualDuration(),
                "Stage 1 manualDuration must be sanitized to null in two-stage mode");
        assertEquals(new BigDecimal("500000"), result.getPreschoolOverride().getManualTuition(),
                "Stage 1 manualTuition must be preserved");

        assertNotNull(result.getPreschoolOverride2());
        assertNull(result.getPreschoolOverride2().getManualDuration(),
                "Stage 2 manualDuration must be sanitized to null in two-stage mode");
        assertEquals(new BigDecimal("1800000"), result.getPreschoolOverride2().getManualTuition(),
                "Stage 2 manualTuition must be preserved");

        ArgumentCaptor<ChildEducationSettings> captor = ArgumentCaptor.forClass(ChildEducationSettings.class);
        verify(settingsRepository).save(captor.capture());
        ChildEducationSettings saved = captor.getValue();
        assertNull(saved.getPreschoolOverride().getManualDuration());
        assertEquals(new BigDecimal("500000"), saved.getPreschoolOverride().getManualTuition());
        assertNull(saved.getPreschoolOverride2().getManualDuration());
        assertEquals(new BigDecimal("1800000"), saved.getPreschoolOverride2().getManualTuition());
    }

    @Test
    void updateSettings_WhenPreschoolSplitDisabled_ClearsTwoStageFields() {
        ChildEducationSettings existing = new ChildEducationSettings(1L);
        existing.setPreschoolSplitEnabled(true);
        existing.setPreschoolStage1Duration(12);
        existing.setPreschoolTrack2(PreschoolTrack.INTL);

        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .preschoolTrack(PreschoolTrack.HOME)
                .preschoolSplitEnabled(false)
                .build();

        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());
        EducationAbsoluteTimeline validTimeline = EducationAbsoluteTimeline.builder()
                .events(Collections.emptyList())
                .build();

        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(existing));
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);
        when(educationTimelineProjector.project(any(), any())).thenReturn(validTimeline);
        when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChildEducationSettingsResult result = service.updateSettings(1L, command);

        assertNotNull(result);
        assertFalse(result.getPreschoolSplitEnabled());
        assertNull(result.getPreschoolStage1Duration());
        assertNull(result.getPreschoolTrack2());
        assertNull(result.getPreschoolOverride2());

        ArgumentCaptor<ChildEducationSettings> captor = ArgumentCaptor.forClass(ChildEducationSettings.class);
        verify(settingsRepository).save(captor.capture());
        ChildEducationSettings saved = captor.getValue();
        assertFalse(saved.getPreschoolSplitEnabled());
        assertNull(saved.getPreschoolStage1Duration());
        assertNull(saved.getPreschoolTrack2());
        assertNull(saved.getPreschoolOverride2());
    }

    @Test
    void updateSettings_WhenPreschoolSplitEnabledAndTrack2IsNull_ThrowsIllegalArgumentException() {
        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(new ChildEducationSettings(1L)));

        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(12)
                .preschoolTrack2(null)
                .build();

        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.updateSettings(1L, command));
        assertTrue(ex.getMessage().contains("preschoolTrack2 must not be null"));
    }

    @Test
    void updateSettings_WhenPreschoolSplitEnabledAndInvalidDuration_ThrowsIllegalArgumentException() {
        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(new ChildEducationSettings(1L)));

        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(18) // Invalid: neither 12 nor 24
                .preschoolTrack2(PreschoolTrack.INTL)
                .build();

        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.updateSettings(1L, command));
        assertTrue(ex.getMessage().contains("preschoolStage1Duration must be either 12 or 24"));
    }

    @Test
    void updateSettings_WhenSingleTrackAndManualDurationLessThan36_ThrowsIllegalArgumentException() {
        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);
        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(new ChildEducationSettings(1L)));

        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
                .preschoolSplitEnabled(false)
                .preschoolOverride(StageOverrideCommand.builder().manualDuration(24).build())
                .build();

        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.updateSettings(1L, command));
        assertTrue(ex.getMessage().contains("must be at least 36 months"));
    }

    @Test
    void deleteSettings_Success() {
        when(ownershipGuard.loadOwned(childProfileRepository, 1L)).thenReturn(childProfile);

        service.deleteSettings(1L);

        verify(settingsRepository).deleteByChildProfileId(1L);
    }
}
