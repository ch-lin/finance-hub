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
package ch.lin.finance.hub.backend.api.app.service.profile.child;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.education.ChildEducationSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.profile.child.command.CreateChildProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.result.ChildProfileResult;
import ch.lin.finance.hub.backend.api.app.service.profile.child.result.EducationProjectionResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationAbsoluteTimeline;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationProfileResolver;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationStage;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationTimelineProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.ResolvedEducationProfile;
import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ChildProfileServiceImplTest {

    @Mock
    private ChildProfileRepository childProfileRepository;

    @Mock
    private EducationConfigService educationConfigService;

    @Mock
    private ChildEducationSettingsRepository childEducationSettingsRepository;

    @Mock
    private EducationProfileResolver educationProfileResolver;

    @Mock
    private EducationTimelineProjector educationTimelineProjector;

    @Mock
    private EducationExpenseCalculator educationExpenseCalculator;

    @InjectMocks
    private ChildProfileServiceImpl service;

    private ChildProfile childProfile;

    @Mock
    private EducationConfig defaultConfig;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        childProfile = ChildProfile.builder()
                .id(1L)
                .name("Hanako")
                .birthDate(LocalDate.of(2018, 4, 15))
                .build();
    }

    @Test
    void getEducationProjections_PopulatesStageRange() {
        ChildEducationSettings settings = new ChildEducationSettings(1L);
        settings.setPrimaryTrack(PrimaryTrack.PUBLIC);

        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());

        EducationAbsoluteTimeline.StageInterval primaryInterval = new EducationAbsoluteTimeline.StageInterval(
                EducationStage.PRIMARY,
                YearMonth.of(2024, 4),
                YearMonth.of(2030, 3),
                BigDecimal.ZERO
        );

        EducationAbsoluteTimeline timeline = EducationAbsoluteTimeline.builder()
                .events(Collections.emptyList())
                .stageIntervals(Map.of(EducationStage.PRIMARY, primaryInterval))
                .build();

        when(childProfileRepository.findById(1L)).thenReturn(Optional.of(childProfile));
        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
        when(childEducationSettingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(settings));
        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);
        when(educationTimelineProjector.project(any(), any())).thenReturn(timeline);
        when(educationExpenseCalculator.calculateAnnualCost(anyInt(), any())).thenReturn(BigDecimal.valueOf(500000));

        List<EducationProjectionResult> results = service.getEducationProjections(1L);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        // Child born 2018. At age 6 (year 2024), primary school starts.
        EducationProjectionResult age6Result = results.stream()
                .filter(r -> r.getAge() == 6)
                .findFirst()
                .orElse(null);

        assertNotNull(age6Result);
        assertEquals(2024, age6Result.getYear());
        assertEquals("Primary", age6Result.getPhase());
        assertEquals("2024-04 ~ 2030-03", age6Result.getStageRange());
    }

    @Test
    void createChildProfile_InitializesDefaultEducationSettings() {
        CreateChildProfileCommand command = CreateChildProfileCommand.builder()
                .name("Ken")
                .birthDate(LocalDate.of(2022, 1, 1))
                .build();

        ChildProfile saved = ChildProfile.builder()
                .id(2L)
                .name("Ken")
                .birthDate(LocalDate.of(2022, 1, 1))
                .build();

        when(childProfileRepository.save(any())).thenReturn(saved);
        when(childEducationSettingsRepository.existsByChildProfileId(2L)).thenReturn(false);

        ChildProfileResult result = service.createChildProfile(command);

        assertNotNull(result);
        assertEquals("Ken", result.getName());
        verify(childEducationSettingsRepository).save(any(ChildEducationSettings.class));
    }
}
