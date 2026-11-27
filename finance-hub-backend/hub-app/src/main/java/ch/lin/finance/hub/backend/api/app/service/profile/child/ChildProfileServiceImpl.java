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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.education.ChildEducationSettingsRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.profile.child.command.CreateChildProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.command.UpdateChildProfileCommand;
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
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChildProfileServiceImpl implements ChildProfileService {

    private final ChildProfileRepository childProfileRepository;
    private final EducationConfigService educationConfigService;
    private final ChildEducationSettingsRepository childEducationSettingsRepository;
    private final EducationProfileResolver educationProfileResolver;
    private final EducationTimelineProjector educationTimelineProjector;
    private final EducationExpenseCalculator educationExpenseCalculator;
    private final OwnershipGuard ownershipGuard;

    @Override
    @Transactional(readOnly = true)
    public List<ChildProfileResult> getChildProfiles() {
        return childProfileRepository.findAll().stream()
                .map(ChildProfileResult::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChildProfileResult getChildProfile(Long childId) {
        return ChildProfileResult.fromEntity(ownershipGuard.loadReadable(childProfileRepository, childId));
    }

    @Override
    @Transactional
    public ChildProfileResult createChildProfile(CreateChildProfileCommand command) {
        ChildProfile childProfile = ChildProfile.builder()
                .name(command.getName())
                .birthDate(command.getBirthDate())
                .build();

        ChildProfile savedProfile = childProfileRepository.save(Objects.requireNonNull(childProfile));

        // Automatically initialize default education settings if not existing
        if (!childEducationSettingsRepository.existsByChildProfileId(savedProfile.getId())) {
            childEducationSettingsRepository.save(new ChildEducationSettings(savedProfile.getId()));
        }

        return ChildProfileResult.fromEntity(savedProfile);
    }

    @Override
    @Transactional
    public ChildProfileResult updateChildProfile(Long childId, UpdateChildProfileCommand command) {
        ChildProfile childProfile = ownershipGuard.loadOwned(childProfileRepository, childId);

        if (command.getName() != null) {
            childProfile.setName(command.getName());
        }
        if (command.getBirthDate() != null) {
            childProfile.setBirthDate(command.getBirthDate());
        }

        return ChildProfileResult.fromEntity(childProfileRepository.save(Objects.requireNonNull(childProfile)));
    }

    @Override
    @Transactional
    public void deleteChildProfile(Long childId) {
        ChildProfile childProfile = ownershipGuard.loadOwned(childProfileRepository, childId);
        childProfileRepository.delete(Objects.requireNonNull(childProfile));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EducationProjectionResult> getEducationProjections(Long childId) {
        ChildProfile child = ownershipGuard.loadReadable(childProfileRepository, childId);
        if (child.getBirthDate() == null) {
            return Collections.emptyList();
        }

        EducationConfig eduConfig = educationConfigService.getDefaultDomainConfig();
        ChildEducationSettings settings = childEducationSettingsRepository.findByChildProfileId(childId)
                .orElseGet(() -> new ChildEducationSettings(childId));

        ResolvedEducationProfile resolvedProfile = educationProfileResolver.resolve(settings, eduConfig);
        EducationAbsoluteTimeline timeline = educationTimelineProjector.project(child.getBirthDate(), resolvedProfile);

        int maxYearOffset = 21;
        if (timeline.getMaxYear() > 0) {
            int timelineMaxYearOffset = timeline.getMaxYear() - child.getBirthDate().getYear();
            maxYearOffset = Math.max(maxYearOffset, timelineMaxYearOffset);
        }

        List<EducationProjectionResult> projection = new ArrayList<>();
        // Note: yearOffset represents calendar years elapsed since the birth year
        // (simulation pointer),
        // not exact biological age. It advances simulationYear to look up annual
        // education costs
        // against the absolute timeline.
        for (int yearOffset = 0; yearOffset <= maxYearOffset; yearOffset++) {
            int simulationYear = child.getBirthDate().getYear() + yearOffset;
            BigDecimal annualCost = educationExpenseCalculator.calculateAnnualCost(simulationYear, timeline);
            long cost = annualCost.setScale(0, RoundingMode.HALF_UP).longValue();

            EducationAbsoluteTimeline.StageInterval interval = resolveIntervalForYear(simulationYear, timeline);
            String phase = interval != null ? formatStageName(interval.stage())
                    : resolveFallbackPhaseForAge(yearOffset);
            String stageRange = interval != null ? interval.startMonth() + " ~ " + interval.endMonth() : null;

            projection.add(EducationProjectionResult.builder()
                    .year(simulationYear)
                    .age(yearOffset)
                    .cost(cost)
                    .phase(phase)
                    .stageRange(stageRange)
                    .build());
        }
        return projection;
    }

    private EducationAbsoluteTimeline.StageInterval resolveIntervalForYear(int year,
            EducationAbsoluteTimeline timeline) {
        if (timeline != null && timeline.getStageIntervals() != null) {
            EducationStage[] stages = EducationStage.values();
            for (int i = stages.length - 1; i >= 0; i--) {
                EducationStage stage = stages[i];
                EducationAbsoluteTimeline.StageInterval interval = timeline.getStageIntervals().get(stage);
                if (interval != null && interval.isActiveInYear(year)) {
                    return interval;
                }
            }
        }
        return null;
    }

    private String resolveFallbackPhaseForAge(int age) {
        if (age <= 5) {
            return "Preschool";
        }
        if (age <= 11) {
            return "Primary";
        }
        if (age <= 14) {
            return "Middle";
        }
        if (age <= 17) {
            return "High";
        }
        return "University";
    }

    private String formatStageName(EducationStage stage) {
        return switch (stage) {
            case PRESCHOOL ->
                "Preschool";
            case PRIMARY ->
                "Primary";
            case MIDDLE ->
                "Middle";
            case HIGH ->
                "High";
            case UNIVERSITY ->
                "University";
        };
    }
}
