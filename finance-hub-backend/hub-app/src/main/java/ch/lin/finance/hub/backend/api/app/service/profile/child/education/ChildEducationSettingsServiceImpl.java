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

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.education.ChildEducationSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
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
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.StageOverride;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ChildEducationSettingsServiceImpl implements ChildEducationSettingsService {

    private final ChildEducationSettingsRepository settingsRepository;
    private final ChildProfileRepository childProfileRepository;
    private final EducationConfigService educationConfigService;
    private final EducationProfileResolver educationProfileResolver;
    private final EducationTimelineProjector educationTimelineProjector;

    @Override
    @Transactional(readOnly = true)
    public ChildEducationSettingsResult getSettings(Long childProfileId) {
        ChildProfile child = childProfileRepository.findById(Objects.requireNonNull(childProfileId))
                .orElseThrow(() -> new IllegalArgumentException("ChildProfile not found with id: " + childProfileId));

        return settingsRepository.findByChildProfileId(childProfileId)
                .map(settings -> ChildEducationSettingsResult.fromEntity(settings, child.getOwnerUserId()))
                .orElseGet(() -> {
                    ChildEducationSettings defaultSettings = new ChildEducationSettings(childProfileId);
                    return ChildEducationSettingsResult.fromEntity(defaultSettings, child.getOwnerUserId());
                });
    }

    @Override
    public ChildEducationSettingsResult updateSettings(Long childProfileId, UpdateChildEducationSettingsCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        ChildProfile child = childProfileRepository.findById(Objects.requireNonNull(childProfileId))
                .orElseThrow(() -> new IllegalArgumentException("ChildProfile not found with id: " + childProfileId));

        ChildEducationSettings settings = settingsRepository.findByChildProfileId(childProfileId)
                .orElseGet(() -> new ChildEducationSettings(childProfileId));

        // Domain invariant: If a stage track is set to NONE (inactive/skipped),
        // purge any corresponding manual overrides and stage-specific auxiliary costs
        // to prevent stale or dirty configuration data from persisting in the database.
        // Note: Preschool does not have a NONE track; HOME represents the zero-cost baseline.
        // In the UI, compulsory stages (Primary, Middle, High) do not offer NONE to preserve
        // timeline continuity. The backend defensively handles NONE for stages supporting skipping.
        if (command.getPreschoolTrack() != null) {
            settings.setPreschoolTrack(command.getPreschoolTrack());
        }
        settings.setPreschoolOverride(command.getPreschoolOverride() != null ? command.getPreschoolOverride().toDomain() : null);
        if (Boolean.TRUE.equals(command.getPreschoolSplitEnabled())) {
            if (command.getPreschoolTrack2() == null) {
                throw new IllegalArgumentException("preschoolTrack2 must not be null when preschoolSplitEnabled is true");
            }
            if (command.getPreschoolStage1Duration() == null
                    || (command.getPreschoolStage1Duration() != 12 && command.getPreschoolStage1Duration() != 24)) {
                throw new IllegalArgumentException("preschoolStage1Duration must be either 12 or 24 months when preschoolSplitEnabled is true");
            }
            // Domain invariant: In two-stage preschool mode, duration is strictly governed by
            // preschoolStage1Duration (12 or 24) and stage 2 (36 - stage1).
            // Sanitize manualDuration on both overrides to prevent stale single-track duration leakage.
            if (settings.getPreschoolOverride() != null) {
                settings.getPreschoolOverride().setManualDuration(null);
            }
            settings.setPreschoolSplitEnabled(true);
            settings.setPreschoolStage1Duration(command.getPreschoolStage1Duration());
            settings.setPreschoolTrack2(command.getPreschoolTrack2());
            StageOverride override2 = command.getPreschoolOverride2() != null ? command.getPreschoolOverride2().toDomain() : null;
            if (override2 != null) {
                override2.setManualDuration(null);
            }
            settings.setPreschoolOverride2(override2);
        } else {
            if (command.getPreschoolOverride() != null
                    && command.getPreschoolOverride().getManualDuration() != null
                    && command.getPreschoolOverride().getManualDuration() < 36) {
                throw new IllegalArgumentException("Preschool manualDuration must be at least 36 months in single-track mode");
            }
            settings.setPreschoolSplitEnabled(false);
            settings.setPreschoolStage1Duration(null);
            settings.setPreschoolTrack2(null);
            settings.setPreschoolOverride2(null);
        }

        if (command.getPrimaryTrack() != null) {
            settings.setPrimaryTrack(command.getPrimaryTrack());
        }
        settings.setPrimaryOverride(command.getPrimaryOverride() != null ? command.getPrimaryOverride().toDomain() : null);

        if (command.getMiddleTrack() != null) {
            settings.setMiddleTrack(command.getMiddleTrack());
        }
        settings.setMiddleOverride(command.getMiddleOverride() != null ? command.getMiddleOverride().toDomain() : null);

        if (command.getHighTrack() != null) {
            settings.setHighTrack(command.getHighTrack());
        }
        if (settings.getHighTrack() == HighTrack.NONE) {
            settings.setHighOverride(null);
        } else {
            settings.setHighOverride(command.getHighOverride() != null ? command.getHighOverride().toDomain() : null);
        }

        if (command.getUniversityTrack() != null) {
            settings.setUniversityTrack(command.getUniversityTrack());
        }
        if (settings.getUniversityTrack() == UniversityTrack.NONE) {
            settings.setUniversityOverride(null);
            settings.setManualLivingCost(null);
            settings.setIsAwayFromHome(false);
        } else {
            settings.setUniversityOverride(command.getUniversityOverride() != null ? command.getUniversityOverride().toDomain() : null);
            settings.setManualLivingCost(command.getManualLivingCost());
            if (command.getIsAwayFromHome() != null) {
                settings.setIsAwayFromHome(command.getIsAwayFromHome());
            }
        }

        validateTimeline(settings, child);

        settings = settingsRepository.save(Objects.requireNonNull(settings));
        return ChildEducationSettingsResult.fromEntity(settings, child.getOwnerUserId());
    }

    private void validateTimeline(ChildEducationSettings settings, ChildProfile child) {
        if (child.getBirthDate() == null) {
            return;
        }

        try {
            EducationConfig eduConfig = educationConfigService.getDefaultDomainConfig();
            ResolvedEducationProfile resolvedProfile = educationProfileResolver.resolve(settings, eduConfig);
            EducationAbsoluteTimeline timeline = educationTimelineProjector.project(child.getBirthDate(), resolvedProfile);

            if (timeline.getMaxYear() > 0) {
                int academicCareerSpan = timeline.getMaxYear() - child.getBirthDate().getYear();
                if (academicCareerSpan > 40) {
                    throw new InvalidEducationTimelineException(
                            "Invalid education timeline: Academic career spans " + academicCareerSpan + " years, exceeding maximum allowable limit (40 years).");
                }
            }
        } catch (InvalidEducationTimelineException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidEducationTimelineException("Invalid education timeline: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteSettings(Long childProfileId) {
        childProfileRepository.findById(Objects.requireNonNull(childProfileId))
                .orElseThrow(() -> new IllegalArgumentException("ChildProfile not found with id: " + childProfileId));

        settingsRepository.deleteByChildProfileId(childProfileId);
    }
}
