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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.TrackConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.UniversityConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.StageOverride;

/**
 * Step 1 of the 3-Step Education Calculation Pipeline. Pure Domain Service
 * responsible for resolving and merging personal overrides
 * (ChildEducationSettings) with global defaults (EducationConfig) into a
 * flattened ResolvedEducationProfile.
 */
@Component
public class EducationProfileResolver {

    public static final Set<Integer> ALLOWED_TERMS = Set.of(1, 2, 3, 4, 6, 12);
    public static final int DEFAULT_TERMS_PER_YEAR = 2;
    public static final int DEFAULT_START_MONTH = 4;

    /**
     * Resolves the flattened profile for all education stages.
     *
     * @param settings The child's education settings with optional overrides
     *                 (can be null).
     * @param config   The global education configuration with default track
     *                 specifications (can be null).
     * @return A flattened ResolvedEducationProfile for all stages.
     */
    public ResolvedEducationProfile resolve(ChildEducationSettings settings, EducationConfig config) {
        Map<EducationStage, ResolvedStageData> stages = new EnumMap<>(EducationStage.class);

        stages.put(EducationStage.PRESCHOOL, resolvePreschool(settings, config));
        stages.put(EducationStage.PRIMARY, resolvePrimary(settings, config));
        stages.put(EducationStage.MIDDLE, resolveMiddle(settings, config));
        stages.put(EducationStage.HIGH, resolveHigh(settings, config));
        stages.put(EducationStage.UNIVERSITY, resolveUniversity(settings, config));

        return ResolvedEducationProfile.builder()
                .stages(Collections.unmodifiableMap(stages))
                .build();
    }

    private ResolvedStageData resolvePreschool(ChildEducationSettings settings, EducationConfig config) {
        Objects.requireNonNull(settings);
        // Preschool defaults to HOME (Home Care = 0 tuition, 36 months) when settings
        // or track is omitted.
        // HOME represents the zero-cost baseline track for preschool; there is no NONE
        // track.
        PreschoolTrack track1 = settings.getPreschoolTrack() != null
                ? settings.getPreschoolTrack()
                : PreschoolTrack.HOME;

        TrackConfig trackConfig1 = getPreschoolTrackConfig(config, track1);
        StageOverride override1 = settings.getPreschoolOverride();

        boolean isTwoStage = Boolean.TRUE.equals(settings.getPreschoolSplitEnabled())
                && settings.getPreschoolTrack2() != null;

        if (!isTwoStage) {
            ResolvedStageData stage1Data = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1,
                    BigDecimal.ZERO);
            // Single-track clamping: duration must be at least 36 months (3 years)
            int clampedDuration = Math.max(36, stage1Data.getDurationMonths());
            if (clampedDuration != stage1Data.getDurationMonths()) {
                return stage1Data.toBuilder()
                        .durationMonths(clampedDuration)
                        .build();
            }
            return stage1Data;
        }

        // Two-stage preschool track transition
        Integer rawD1 = settings.getPreschoolStage1Duration();
        int d1 = (rawD1 != null && (rawD1 == 12 || rawD1 == 24)) ? rawD1 : 12;
        int d2 = 36 - d1;

        ResolvedStageData stage1Base = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1,
                BigDecimal.ZERO);

        PreschoolTrack track2 = settings.getPreschoolTrack2();
        TrackConfig trackConfig2 = getPreschoolTrackConfig(config, track2);
        StageOverride override2 = settings.getPreschoolOverride2();
        ResolvedStageData stage2Base = mergeStage(EducationStage.PRESCHOOL, track2.name(), trackConfig2, override2,
                BigDecimal.ZERO);

        ResolvedStageData stage2Data = stage2Base.toBuilder()
                .durationMonths(d2)
                .build();

        return stage1Base.toBuilder()
                .durationMonths(d1)
                .stage2(stage2Data)
                .build();
    }

    private TrackConfig getPreschoolTrackConfig(EducationConfig config, PreschoolTrack track) {
        if (config == null || config.getPreschool() == null || track == null) {
            return null;
        }
        return switch (track) {
            case HOME ->
                config.getPreschool().getHomeTrack();
            case DAYCARE ->
                config.getPreschool().getDaycareTrack();
            case INTL ->
                config.getPreschool().getIntlTrack();
        };
    }

    private ResolvedStageData resolvePrimary(ChildEducationSettings settings, EducationConfig config) {
        PrimaryTrack track = settings != null && settings.getPrimaryTrack() != null
                ? settings.getPrimaryTrack()
                : PrimaryTrack.PUBLIC;

        TrackConfig trackConfig = null;
        if (config != null && config.getPrimary() != null) {
            trackConfig = switch (track) {
                case PUBLIC ->
                    config.getPrimary().getPublicTrack();
                case PRIVATE ->
                    config.getPrimary().getPrivateTrack();
                case INTL ->
                    config.getPrimary().getIntlTrack();
            };
        }

        StageOverride override = settings != null ? settings.getPrimaryOverride() : null;
        return mergeStage(EducationStage.PRIMARY, track.name(), trackConfig, override, BigDecimal.ZERO);
    }

    private ResolvedStageData resolveMiddle(ChildEducationSettings settings, EducationConfig config) {
        MiddleTrack track = settings != null && settings.getMiddleTrack() != null
                ? settings.getMiddleTrack()
                : MiddleTrack.PUBLIC;

        TrackConfig trackConfig = null;
        if (config != null && config.getMiddle() != null) {
            trackConfig = switch (track) {
                case PUBLIC ->
                    config.getMiddle().getPublicTrack();
                case PRIVATE ->
                    config.getMiddle().getPrivateTrack();
                case INTL ->
                    config.getMiddle().getIntlTrack();
            };
        }

        StageOverride override = settings != null ? settings.getMiddleOverride() : null;
        return mergeStage(EducationStage.MIDDLE, track.name(), trackConfig, override, BigDecimal.ZERO);
    }

    private ResolvedStageData resolveHigh(ChildEducationSettings settings, EducationConfig config) {
        HighTrack track = settings != null && settings.getHighTrack() != null
                ? settings.getHighTrack()
                : HighTrack.NONE;

        if (track == HighTrack.NONE) {
            return inactiveStage(EducationStage.HIGH, HighTrack.NONE.name());
        }

        TrackConfig trackConfig = null;
        if (config != null && config.getHigh() != null) {
            trackConfig = switch (track) {
                case PUBLIC ->
                    config.getHigh().getPublicTrack();
                case PRIVATE ->
                    config.getHigh().getPrivateTrack();
                case INTL ->
                    config.getHigh().getIntlTrack();
                case NONE ->
                    null;
            };
        }

        StageOverride override = settings != null ? settings.getHighOverride() : null;
        return mergeStage(EducationStage.HIGH, track.name(), trackConfig, override, BigDecimal.ZERO);
    }

    private ResolvedStageData resolveUniversity(ChildEducationSettings settings, EducationConfig config) {
        UniversityTrack track = settings != null && settings.getUniversityTrack() != null
                ? settings.getUniversityTrack()
                : UniversityTrack.NONE;

        if (track == UniversityTrack.NONE) {
            return inactiveStage(EducationStage.UNIVERSITY, UniversityTrack.NONE.name());
        }

        TrackConfig trackConfig = null;
        UniversityConfig uniConfig = config != null ? config.getUniversity() : null;
        if (uniConfig != null) {
            trackConfig = switch (track) {
                case JP_NATIONAL ->
                    uniConfig.getJpNationalTrack();
                case JP_PRIVATE ->
                    uniConfig.getJpPrivateTrack();
                case JP_PRIVATE_ART ->
                    uniConfig.getJpPrivateArtTrack();
                case TW_NATIONAL ->
                    uniConfig.getTwNationalTrack();
                case TW_PRIVATE ->
                    uniConfig.getTwPrivateTrack();
                case US ->
                    uniConfig.getUsTrack();
                case UK ->
                    uniConfig.getUkTrack();
                case CANADA ->
                    uniConfig.getCanadaTrack();
                case AUSTRALIA ->
                    uniConfig.getAustraliaTrack();
                case NONE ->
                    null;
            };
        }

        BigDecimal livingCost = BigDecimal.ZERO;
        if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
            livingCost = (settings.getManualLivingCost() != null)
                    ? settings.getManualLivingCost()
                    : resolveDefaultUniversityLivingCost(track, uniConfig);
        }

        StageOverride override = settings != null ? settings.getUniversityOverride() : null;
        return mergeStage(EducationStage.UNIVERSITY, track.name(), trackConfig, override, livingCost);
    }

    private BigDecimal resolveDefaultUniversityLivingCost(UniversityTrack track, UniversityConfig uniConfig) {
        if (uniConfig == null || track == null || track == UniversityTrack.NONE) {
            return BigDecimal.ZERO;
        }

        BigDecimal countryLivingCost = switch (track) {
            case JP_NATIONAL, JP_PRIVATE, JP_PRIVATE_ART -> uniConfig.getJpLivingCost();
            case TW_NATIONAL, TW_PRIVATE -> uniConfig.getTwLivingCost();
            case US -> uniConfig.getUsLivingCost();
            case UK -> uniConfig.getUkLivingCost();
            case CANADA -> uniConfig.getCanadaLivingCost();
            case AUSTRALIA -> uniConfig.getAustraliaLivingCost();
            case NONE -> null;
        };

        if (countryLivingCost != null) {
            return countryLivingCost;
        }
        return uniConfig.getLivingCost() != null ? uniConfig.getLivingCost() : BigDecimal.ZERO;
    }

    private ResolvedStageData mergeStage(
            EducationStage stage,
            String trackName,
            TrackConfig defaultTrack,
            StageOverride override,
            BigDecimal livingCost) {

        BigDecimal tuition = BigDecimal.ZERO;
        if (override != null && override.getManualTuition() != null) {
            tuition = override.getManualTuition();
        } else if (defaultTrack != null && defaultTrack.getTuition() != null) {
            tuition = defaultTrack.getTuition();
        }

        int durationMonths = 0;
        if (override != null && override.getManualDuration() != null) {
            durationMonths = normalizeDurationToMonths(override.getManualDuration());
        } else if (defaultTrack != null && defaultTrack.getDuration() != null) {
            durationMonths = normalizeDurationToMonths(defaultTrack.getDuration());
        }

        int startMonth = DEFAULT_START_MONTH;
        if (override != null && override.getManualStartMonth() != null) {
            startMonth = override.getManualStartMonth();
        } else if (defaultTrack != null && defaultTrack.getStartMonth() != null) {
            startMonth = defaultTrack.getStartMonth();
        }

        int termsPerYear = DEFAULT_TERMS_PER_YEAR;
        if (override != null && override.getManualTermsPerYear() != null) {
            termsPerYear = override.getManualTermsPerYear();
        } else if (defaultTrack != null && defaultTrack.getTermsPerYear() != null) {
            termsPerYear = defaultTrack.getTermsPerYear();
        }

        if (!ALLOWED_TERMS.contains(termsPerYear)) {
            throw new IllegalArgumentException(
                    "Invalid termsPerYear: " + termsPerYear + ". Allowed values: " + ALLOWED_TERMS);
        }

        BigDecimal entranceFee = BigDecimal.ZERO;
        if (defaultTrack != null && defaultTrack.getEntranceFee() != null) {
            entranceFee = defaultTrack.getEntranceFee();
        }

        List<Integer> paymentMonths = calculatePaymentMonths(startMonth, termsPerYear);
        Integer standardEntranceAge = defaultTrack != null ? defaultTrack.getStandardEntranceAge() : null;

        return ResolvedStageData.builder()
                .stage(stage)
                .trackName(trackName)
                .active(true)
                .annualTuition(tuition)
                .durationMonths(durationMonths)
                .startMonth(startMonth)
                .termsPerYear(termsPerYear)
                .entranceFee(entranceFee)
                .monthlyLivingCost(livingCost != null ? livingCost : BigDecimal.ZERO)
                .paymentMonths(paymentMonths)
                .standardEntranceAge(standardEntranceAge)
                .build();
    }

    private ResolvedStageData inactiveStage(EducationStage stage, String trackName) {
        return ResolvedStageData.builder()
                .stage(stage)
                .trackName(trackName)
                .active(false)
                .annualTuition(BigDecimal.ZERO)
                .durationMonths(0)
                .startMonth(DEFAULT_START_MONTH)
                .termsPerYear(DEFAULT_TERMS_PER_YEAR)
                .entranceFee(BigDecimal.ZERO)
                .monthlyLivingCost(BigDecimal.ZERO)
                .paymentMonths(Collections.emptyList())
                .build();
    }

    /**
     * Calculates payment months evenly spaced throughout the year starting from
     * startMonth.
     *
     * @param startMonth   1-12
     * @param termsPerYear Whitelisted term count in {1, 2, 3, 4, 6, 12}
     * @return List of 1-based months
     */
    public static List<Integer> calculatePaymentMonths(int startMonth, int termsPerYear) {
        if (!ALLOWED_TERMS.contains(termsPerYear)) {
            throw new IllegalArgumentException(
                    "Invalid termsPerYear: " + termsPerYear + ". Allowed values: " + ALLOWED_TERMS);
        }
        if (startMonth < 1 || startMonth > 12) {
            throw new IllegalArgumentException("Invalid startMonth: " + startMonth + ". Must be between 1 and 12.");
        }
        int interval = 12 / termsPerYear;
        List<Integer> months = new ArrayList<>(termsPerYear);
        for (int i = 0; i < termsPerYear; i++) {
            int month = ((startMonth - 1 + i * interval) % 12) + 1;
            months.add(month);
        }
        return Collections.unmodifiableList(months);
    }

    /**
     * Validates and returns the duration in months. All system configurations
     * and overrides are standardly represented in months.
     *
     * @param duration duration in months
     * @return duration in months, or 0 if null or negative
     */
    public static int normalizeDurationToMonths(Integer duration) {
        if (duration == null || duration <= 0) {
            return 0;
        }
        return duration;
    }
}
