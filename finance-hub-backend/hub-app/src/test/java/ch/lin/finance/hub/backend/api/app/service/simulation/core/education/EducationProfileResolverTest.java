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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.HighConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.MiddleConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.PreschoolConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.PrimaryConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.TrackConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.UniversityConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.StageOverride;

class EducationProfileResolverTest {

    private EducationProfileResolver resolver;
    private EducationConfig defaultConfig;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        resolver = new EducationProfileResolver();

        defaultConfig = EducationConfig.builder()
                .name("default")
                .preschool(PreschoolConfig.builder()
                        .homeTrack(TrackConfig.builder()
                                .tuition(BigDecimal.ZERO)
                                .duration(36)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(3)
                                .build())
                        .daycareTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("300000"))
                                .duration(36)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(3)
                                .build())
                        .intlTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("1500000"))
                                .duration(36)
                                .startMonth(9)
                                .termsPerYear(3)
                                .entranceFee(new BigDecimal("200000"))
                                .standardEntranceAge(3)
                                .build())
                        .build())
                .primary(PrimaryConfig.builder()
                        .publicTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("50000"))
                                .duration(72)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(6)
                                .build())
                        .privateTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("800000"))
                                .duration(72)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(new BigDecimal("300000"))
                                .standardEntranceAge(6)
                                .build())
                        .intlTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("2000000"))
                                .duration(72)
                                .startMonth(9)
                                .termsPerYear(3)
                                .entranceFee(new BigDecimal("500000"))
                                .standardEntranceAge(6)
                                .build())
                        .build())
                .middle(MiddleConfig.builder()
                        .publicTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("100000"))
                                .duration(36)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(12)
                                .build())
                        .privateTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("1000000"))
                                .duration(36)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(new BigDecimal("300000"))
                                .standardEntranceAge(12)
                                .build())
                        .intlTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("2200000"))
                                .duration(36)
                                .startMonth(9)
                                .termsPerYear(3)
                                .entranceFee(new BigDecimal("500000"))
                                .standardEntranceAge(12)
                                .build())
                        .build())
                .high(HighConfig.builder()
                        .publicTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("150000"))
                                .duration(36)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(15)
                                .build())
                        .privateTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("1200000"))
                                .duration(36)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(new BigDecimal("350000"))
                                .standardEntranceAge(15)
                                .build())
                        .intlTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("2500000"))
                                .duration(36)
                                .startMonth(9)
                                .termsPerYear(3)
                                .entranceFee(new BigDecimal("500000"))
                                .standardEntranceAge(15)
                                .build())
                        .build())
                .university(UniversityConfig.builder()
                        .jpNationalTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("500000"))
                                .duration(48)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(new BigDecimal("280000"))
                                .standardEntranceAge(18)
                                .build())
                        .jpPrivateTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("900000"))
                                .duration(48)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(new BigDecimal("200000"))
                                .standardEntranceAge(18)
                                .build())
                        .jpPrivateArtTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("1200000"))
                                .duration(48)
                                .startMonth(4)
                                .termsPerYear(2)
                                .entranceFee(new BigDecimal("300000"))
                                .standardEntranceAge(18)
                                .build())
                        .twNationalTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("300000"))
                                .duration(48)
                                .startMonth(9)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(18)
                                .build())
                        .twPrivateTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("600000"))
                                .duration(48)
                                .startMonth(9)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(18)
                                .build())
                        .usTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("6000000"))
                                .duration(48)
                                .startMonth(9)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(18)
                                .build())
                        .ukTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("5000000"))
                                .duration(36)
                                .startMonth(9)
                                .termsPerYear(3)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(18)
                                .build())
                        .canadaTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("4000000"))
                                .duration(48)
                                .startMonth(9)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(18)
                                .build())
                        .australiaTrack(TrackConfig.builder()
                                .tuition(new BigDecimal("4500000"))
                                .duration(36)
                                .startMonth(2)
                                .termsPerYear(2)
                                .entranceFee(BigDecimal.ZERO)
                                .standardEntranceAge(18)
                                .build())
                        .jpLivingCost(new BigDecimal("130000"))
                        .twLivingCost(new BigDecimal("70000"))
                        .usLivingCost(new BigDecimal("280000"))
                        .ukLivingCost(new BigDecimal("250000"))
                        .canadaLivingCost(new BigDecimal("220000"))
                        .australiaLivingCost(new BigDecimal("230000"))
                        .livingCost(new BigDecimal("125000"))
                        .build())
                .build();
    }

    @Test
    @DisplayName("Should correctly calculate payment months for all whitelisted terms")
    void shouldCalculatePaymentMonthsCorrectly() {
        // 1 term (Annual)
        assertThat(EducationProfileResolver.calculatePaymentMonths(4, 1))
                .containsExactly(4);

        // 2 terms (Semester: Japan standard)
        assertThat(EducationProfileResolver.calculatePaymentMonths(4, 2))
                .containsExactly(4, 10);

        // 3 terms (Trimester: UK / Intl standard starting in September)
        assertThat(EducationProfileResolver.calculatePaymentMonths(9, 3))
                .containsExactly(9, 1, 5);

        // 4 terms (Quarter: US starting in September)
        assertThat(EducationProfileResolver.calculatePaymentMonths(9, 4))
                .containsExactly(9, 12, 3, 6);

        // 6 terms (Bi-monthly)
        assertThat(EducationProfileResolver.calculatePaymentMonths(1, 6))
                .containsExactly(1, 3, 5, 7, 9, 11);

        // 12 terms (Monthly)
        assertThat(EducationProfileResolver.calculatePaymentMonths(4, 12))
                .containsExactly(4, 5, 6, 7, 8, 9, 10, 11, 12, 1, 2, 3);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for non-whitelisted terms")
    void shouldRejectInvalidTerms() {
        assertThatThrownBy(() -> EducationProfileResolver.calculatePaymentMonths(4, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid termsPerYear: 5");

        assertThatThrownBy(() -> EducationProfileResolver.calculatePaymentMonths(4, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should correctly validate and return duration in months")
    void shouldNormalizeDuration() {
        assertThat(EducationProfileResolver.normalizeDurationToMonths(36)).isEqualTo(36);
        assertThat(EducationProfileResolver.normalizeDurationToMonths(72)).isEqualTo(72);
        assertThat(EducationProfileResolver.normalizeDurationToMonths(48)).isEqualTo(48);
        assertThat(EducationProfileResolver.normalizeDurationToMonths(24)).isEqualTo(24);
        assertThat(EducationProfileResolver.normalizeDurationToMonths(0)).isEqualTo(0);
        assertThat(EducationProfileResolver.normalizeDurationToMonths(-1)).isEqualTo(0);
        assertThat(EducationProfileResolver.normalizeDurationToMonths(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("Should resolve default config when child settings has no overrides")
    void shouldResolveDefaultConfigWithoutOverrides() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.DAYCARE)
                .primaryTrack(PrimaryTrack.PUBLIC)
                .middleTrack(MiddleTrack.PUBLIC)
                .highTrack(HighTrack.PUBLIC)
                .universityTrack(UniversityTrack.JP_NATIONAL)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);

        // Preschool
        ResolvedStageData preschool = profile.getStage(EducationStage.PRESCHOOL);
        assertThat(preschool.isActive()).isTrue();
        assertThat(preschool.getAnnualTuition()).isEqualByComparingTo("300000");
        assertThat(preschool.getDurationMonths()).isEqualTo(36); // 3 years -> 36 months
        assertThat(preschool.getStartMonth()).isEqualTo(4);
        assertThat(preschool.getPaymentMonths()).containsExactly(4, 10);
        assertThat(preschool.getStandardEntranceAge()).isEqualTo(3);

        // Primary
        ResolvedStageData primary = profile.getStage(EducationStage.PRIMARY);
        assertThat(primary.isActive()).isTrue();
        assertThat(primary.getAnnualTuition()).isEqualByComparingTo("50000");
        assertThat(primary.getDurationMonths()).isEqualTo(72); // 6 years -> 72 months
        assertThat(primary.getStartMonth()).isEqualTo(4);
        assertThat(primary.getPaymentMonths()).containsExactly(4, 10);
        assertThat(primary.getStandardEntranceAge()).isEqualTo(6);

        // Middle
        ResolvedStageData middle = profile.getStage(EducationStage.MIDDLE);
        assertThat(middle.isActive()).isTrue();
        assertThat(middle.getAnnualTuition()).isEqualByComparingTo("100000");
        assertThat(middle.getStandardEntranceAge()).isEqualTo(12);

        // High
        ResolvedStageData high = profile.getStage(EducationStage.HIGH);
        assertThat(high.isActive()).isTrue();
        assertThat(high.getStandardEntranceAge()).isEqualTo(15);

        // University (National, awayFromHome = false by default -> livingCost is 0)
        ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);
        assertThat(uni.isActive()).isTrue();
        assertThat(uni.getAnnualTuition()).isEqualByComparingTo("500000");
        assertThat(uni.getEntranceFee()).isEqualByComparingTo("280000");
        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(uni.getStandardEntranceAge()).isEqualTo(18);
    }

    @Test
    @DisplayName("Should mark NONE track as inactive and compulsory stages as active")
    void shouldHandleNoneTrack() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.HOME)
                .primaryTrack(PrimaryTrack.PUBLIC)
                .middleTrack(MiddleTrack.PUBLIC)
                .highTrack(HighTrack.NONE)
                .universityTrack(UniversityTrack.NONE)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);

        // Preschool HOME is active with 0 tuition
        assertThat(profile.hasStage(EducationStage.PRESCHOOL)).isTrue();
        assertThat(profile.getStage(EducationStage.PRESCHOOL).isActive()).isTrue();
        assertThat(profile.getStage(EducationStage.PRESCHOOL).getAnnualTuition()).isEqualByComparingTo(BigDecimal.ZERO);

        // Compulsory stages remain active
        assertThat(profile.hasStage(EducationStage.PRIMARY)).isTrue();
        assertThat(profile.getStage(EducationStage.PRIMARY).isActive()).isTrue();

        assertThat(profile.hasStage(EducationStage.MIDDLE)).isTrue();
        assertThat(profile.getStage(EducationStage.MIDDLE).isActive()).isTrue();

        // Stages set to NONE are marked inactive
        assertThat(profile.hasStage(EducationStage.HIGH)).isFalse();
        assertThat(profile.getStage(EducationStage.HIGH).isActive()).isFalse();

        assertThat(profile.hasStage(EducationStage.UNIVERSITY)).isFalse();
        assertThat(profile.getStage(EducationStage.UNIVERSITY).isActive()).isFalse();
    }

    @Test
    @DisplayName("Should apply personal overrides and recompute payment months")
    void shouldApplyPersonalOverrides() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.INTL)
                .preschoolOverride(StageOverride.builder()
                        .manualTuition(new BigDecimal("1800000"))
                        .manualDuration(24) // entered K4: 2 years (24 months)
                        .manualStartMonth(9)
                        .manualTermsPerYear(3) // 3 terms -> [9, 1, 5]
                        .build())
                .primaryTrack(PrimaryTrack.INTL)
                .middleTrack(MiddleTrack.PUBLIC)
                .highTrack(HighTrack.PUBLIC)
                .universityTrack(UniversityTrack.UK)
                .isAwayFromHome(true)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);

        // Preschool override
        ResolvedStageData preschool = profile.getStage(EducationStage.PRESCHOOL);
        assertThat(preschool.isActive()).isTrue();
        // Preschool single-track duration is clamped to at least 36 months per FR-125
        assertThat(preschool.getDurationMonths()).isEqualTo(36);
        assertThat(preschool.getStartMonth()).isEqualTo(9);
        assertThat(preschool.getTermsPerYear()).isEqualTo(3);
        assertThat(preschool.getPaymentMonths()).containsExactly(9, 1, 5);
        // entranceFee falls back to intl default (200000)
        assertThat(preschool.getEntranceFee()).isEqualByComparingTo("200000");

        // University UK: automatic overseas living cost
        ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);
        assertThat(uni.getAnnualTuition()).isEqualByComparingTo("5000000");
        assertThat(uni.getDurationMonths()).isEqualTo(36);
        assertThat(uni.getStartMonth()).isEqualTo(9);
        assertThat(uni.getTermsPerYear()).isEqualTo(3);
        assertThat(uni.getPaymentMonths()).containsExactly(9, 1, 5);
        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("250000");
    }

    @Test
    @DisplayName("Should apply manual living cost override for university")
    void shouldApplyManualLivingCost() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.JP_NATIONAL)
                .isAwayFromHome(true)
                .manualLivingCost(new BigDecimal("100000"))
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);

        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Should resolve country-specific living cost for all tracks when away from home")
    void shouldResolveCountrySpecificLivingCostWhenAwayFromHome() {
        // Japan National: 130,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.JP_NATIONAL)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("130000");

        // Japan Private: 130,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.JP_PRIVATE)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("130000");

        // Japan Private Art: 130,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.JP_PRIVATE_ART)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("130000");

        // Taiwan National: 70,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.TW_NATIONAL)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("70000");

        // Taiwan Private: 70,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.TW_PRIVATE)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("70000");

        // US: 280,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.US)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("280000");

        // UK: 250,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.UK)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("250000");

        // Canada: 220,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.CANADA)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("220000");

        // Australia: 230,000
        assertThat(resolver.resolve(ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.AUSTRALIA)
                .isAwayFromHome(true).build(), defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo("230000");
    }

    @Test
    @DisplayName("Should fallback to generic living cost when country-specific living cost is null")
    void shouldFallbackToGenericLivingCostWhenCountryLivingCostIsNull() {
        UniversityConfig customUni = UniversityConfig.builder()
                .usTrack(defaultConfig.getUniversity().getUsTrack())
                .livingCost(new BigDecimal("125000"))
                // usLivingCost is null
                .build();
        EducationConfig customConfig = EducationConfig.builder()
                .name("custom")
                .preschool(defaultConfig.getPreschool())
                .primary(defaultConfig.getPrimary())
                .middle(defaultConfig.getMiddle())
                .high(defaultConfig.getHigh())
                .university(customUni)
                .build();

        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.US)
                .isAwayFromHome(true)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, customConfig);
        ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);

        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("125000");
    }

    @Test
    @DisplayName("Should resolve zero living cost when awayFromHome is false or null and no manual override")
    void shouldResolveZeroLivingCostWhenNotAwayFromHome() {
        ChildEducationSettings settingsFalse = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.US)
                .isAwayFromHome(false)
                .build();
        assertThat(resolver.resolve(settingsFalse, defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo(BigDecimal.ZERO);

        ChildEducationSettings settingsNull = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.US)
                .isAwayFromHome(null)
                .build();
        assertThat(resolver.resolve(settingsNull, defaultConfig)
                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should resolve default config for JP_PRIVATE university track")
    void shouldResolveJpPrivateUniversityTrack() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.JP_PRIVATE)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);

        assertThat(uni.isActive()).isTrue();
        assertThat(uni.getAnnualTuition()).isEqualByComparingTo("900000");
        assertThat(uni.getEntranceFee()).isEqualByComparingTo("200000");
        assertThat(uni.getDurationMonths()).isEqualTo(48);
        assertThat(uni.getStartMonth()).isEqualTo(4);
        assertThat(uni.getTermsPerYear()).isEqualTo(2);
        assertThat(uni.getPaymentMonths()).containsExactly(4, 10);
    }

    @Test
    @DisplayName("Should resolve default config for TW_NATIONAL university track")
    void shouldResolveTwNationalUniversityTrack() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.TW_NATIONAL)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);

        assertThat(uni.isActive()).isTrue();
        assertThat(uni.getAnnualTuition()).isEqualByComparingTo("300000");
        assertThat(uni.getEntranceFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(uni.getDurationMonths()).isEqualTo(48);
        assertThat(uni.getStartMonth()).isEqualTo(9);
        assertThat(uni.getTermsPerYear()).isEqualTo(2);
        assertThat(uni.getPaymentMonths()).containsExactly(9, 3);
    }

    @Test
    @DisplayName("Should clamp preschool duration to 36 months in single-track mode when manual duration is less than 36")
    void shouldClampPreschoolDurationInSingleTrackMode() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.INTL)
                .preschoolOverride(StageOverride.builder()
                        .manualDuration(12) // Malicious or mistaken 12 months input
                        .build())
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData preschool = profile.getStage(EducationStage.PRESCHOOL);

        assertThat(preschool.isActive()).isTrue();
        assertThat(preschool.getDurationMonths()).isEqualTo(36);
        assertThat(preschool.hasStage2()).isFalse();
    }

    @Test
    @DisplayName("Should preserve preschool duration when manual duration is greater than or equal to 36 months")
    void shouldPreservePreschoolDurationWhenGreaterOrEqualTo36Months() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.INTL)
                .preschoolOverride(StageOverride.builder()
                        .manualDuration(48) // Delayed entrance / gap year
                        .build())
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData preschool = profile.getStage(EducationStage.PRESCHOOL);

        assertThat(preschool.isActive()).isTrue();
        assertThat(preschool.getDurationMonths()).isEqualTo(48);
        assertThat(preschool.hasStage2()).isFalse();
    }

    @Test
    @DisplayName("Should resolve two-stage preschool with HOME (12 mos) and INTL (24 mos)")
    void shouldResolveTwoStagePreschoolHomeThenIntl() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.HOME)
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(12)
                .preschoolTrack2(PreschoolTrack.INTL)
                .preschoolOverride2(StageOverride.builder()
                        .manualTuition(new BigDecimal("1600000"))
                        .build())
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData stage1 = profile.getStage(EducationStage.PRESCHOOL);

        assertThat(stage1).isNotNull();
        assertThat(stage1.isActive()).isTrue();
        assertThat(stage1.getTrackName()).isEqualTo("HOME");
        assertThat(stage1.getDurationMonths()).isEqualTo(12);
        assertThat(stage1.getAnnualTuition()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stage1.hasStage2()).isTrue();

        ResolvedStageData stage2 = stage1.getStage2();
        assertThat(stage2).isNotNull();
        assertThat(stage2.isActive()).isTrue();
        assertThat(stage2.getTrackName()).isEqualTo("INTL");
        assertThat(stage2.getDurationMonths()).isEqualTo(24);
        assertThat(stage2.getAnnualTuition()).isEqualByComparingTo("1600000"); // overridden tuition
        assertThat(stage2.getEntranceFee()).isEqualByComparingTo("200000"); // INTL default entrance fee
        assertThat(stage2.getStartMonth()).isEqualTo(9);
        assertThat(stage2.getTermsPerYear()).isEqualTo(3);
        assertThat(stage2.getPaymentMonths()).containsExactly(9, 1, 5);
    }

    @Test
    @DisplayName("Should resolve two-stage preschool with stage 1 duration = 24 months and stage 2 = 12 months")
    void shouldResolveTwoStagePreschoolWithCustomDuration24Months() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.DAYCARE)
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(24)
                .preschoolTrack2(PreschoolTrack.INTL)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData stage1 = profile.getStage(EducationStage.PRESCHOOL);

        assertThat(stage1.getTrackName()).isEqualTo("DAYCARE");
        assertThat(stage1.getDurationMonths()).isEqualTo(24);
        assertThat(stage1.getAnnualTuition()).isEqualByComparingTo("300000");
        assertThat(stage1.hasStage2()).isTrue();

        ResolvedStageData stage2 = stage1.getStage2();
        assertThat(stage2.getTrackName()).isEqualTo("INTL");
        assertThat(stage2.getDurationMonths()).isEqualTo(12);
        assertThat(stage2.getAnnualTuition()).isEqualByComparingTo("1500000");
    }

    @Test
    @DisplayName("Should default to 12 months when two-stage preschool has invalid stage1 duration")
    void shouldDefaultTo12MonthsWhenTwoStagePreschoolHasInvalidDuration() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.HOME)
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(99) // Invalid value (neither 12 nor 24)
                .preschoolTrack2(PreschoolTrack.INTL)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData stage1 = profile.getStage(EducationStage.PRESCHOOL);

        assertThat(stage1.getDurationMonths()).isEqualTo(12);
        assertThat(stage1.hasStage2()).isTrue();
        assertThat(stage1.getStage2().getDurationMonths()).isEqualTo(24);
    }

    @Test
    @DisplayName("Should fallback to single-track mode when splitEnabled is true but track2 is null")
    void shouldFallbackToSingleTrackWhenSplitEnabledButTrack2IsNull() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .preschoolTrack(PreschoolTrack.HOME)
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(12)
                .preschoolTrack2(null) // Missing track2
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData stage = profile.getStage(EducationStage.PRESCHOOL);

        assertThat(stage.getTrackName()).isEqualTo("HOME");
        assertThat(stage.getDurationMonths()).isEqualTo(36);
        assertThat(stage.hasStage2()).isFalse();
    }

    @Test
    @DisplayName("Should ignore manualLivingCost and calculate zero living cost when isAwayFromHome is false")
    void shouldIgnoreManualLivingCostWhenIsAwayFromHomeIsFalse() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.US)
                .isAwayFromHome(false)
                .manualLivingCost(new BigDecimal("350000"))
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData stage = profile.getStage(EducationStage.UNIVERSITY);

        assertThat(stage).isNotNull();
        assertThat(stage.getMonthlyLivingCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should adopt manualLivingCost when isAwayFromHome is true")
    void shouldAdoptManualLivingCostWhenIsAwayFromHomeIsTrue() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.US)
                .isAwayFromHome(true)
                .manualLivingCost(new BigDecimal("350000"))
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData stage = profile.getStage(EducationStage.UNIVERSITY);

        assertThat(stage).isNotNull();
        assertThat(stage.getMonthlyLivingCost()).isEqualByComparingTo(new BigDecimal("350000"));
    }

    @Test
    @DisplayName("Should use default university living cost when isAwayFromHome is true and manualLivingCost is null")
    void shouldUseDefaultLivingCostWhenIsAwayFromHomeIsTrueAndManualLivingCostIsNull() {
        ChildEducationSettings settings = ChildEducationSettings.builder()
                .childProfileId(1L)
                .universityTrack(UniversityTrack.US)
                .isAwayFromHome(true)
                .manualLivingCost(null)
                .build();

        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
        ResolvedStageData stage = profile.getStage(EducationStage.UNIVERSITY);

        assertThat(stage).isNotNull();
        BigDecimal expectedMonthly = defaultConfig.getUniversity().getUsLivingCost();
        assertThat(stage.getMonthlyLivingCost()).isEqualByComparingTo(expectedMonthly);
    }
}
