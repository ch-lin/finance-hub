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
package ch.lin.finance.hub.backend.api.app.service.config.education.result;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.HighConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.MiddleConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.PreschoolConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.PrimaryConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.TrackConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.UniversityConfig;
import lombok.Builder;
import lombok.Value;

/**
 * Result DTO for EducationConfig that encapsulates education stages and tracks
 * without exposing domain JPA entities to outer layers.
 */
@Value
@Builder
public class EducationConfigResult {

    String name;
    PreschoolConfigResult preschool;
    PrimaryConfigResult primary;
    MiddleConfigResult middle;
    HighConfigResult high;
    UniversityConfigResult university;

    @Value
    @Builder
    public static class TrackConfigResult {

        BigDecimal tuition;
        Integer duration;
        Integer startMonth;
        Integer termsPerYear;
        BigDecimal entranceFee;
        Integer standardEntranceAge;

        public static TrackConfigResult fromEntity(TrackConfig entity) {
            if (entity == null) {
                return null;
            }
            return TrackConfigResult.builder()
                    .tuition(entity.getTuition())
                    .duration(entity.getDuration())
                    .startMonth(entity.getStartMonth())
                    .termsPerYear(entity.getTermsPerYear())
                    .entranceFee(entity.getEntranceFee())
                    .standardEntranceAge(entity.getStandardEntranceAge())
                    .build();
        }
    }

    @Value
    @Builder
    public static class PreschoolConfigResult {

        TrackConfigResult homeTrack;
        TrackConfigResult daycareTrack;
        TrackConfigResult intlTrack;

        public static PreschoolConfigResult fromEntity(PreschoolConfig entity) {
            if (entity == null) {
                return null;
            }
            return PreschoolConfigResult.builder()
                    .homeTrack(TrackConfigResult.fromEntity(entity.getHomeTrack()))
                    .daycareTrack(TrackConfigResult.fromEntity(entity.getDaycareTrack()))
                    .intlTrack(TrackConfigResult.fromEntity(entity.getIntlTrack()))
                    .build();
        }
    }

    @Value
    @Builder
    public static class PrimaryConfigResult {

        TrackConfigResult publicTrack;
        TrackConfigResult privateTrack;
        TrackConfigResult intlTrack;

        public static PrimaryConfigResult fromEntity(PrimaryConfig entity) {
            if (entity == null) {
                return null;
            }
            return PrimaryConfigResult.builder()
                    .publicTrack(TrackConfigResult.fromEntity(entity.getPublicTrack()))
                    .privateTrack(TrackConfigResult.fromEntity(entity.getPrivateTrack()))
                    .intlTrack(TrackConfigResult.fromEntity(entity.getIntlTrack()))
                    .build();
        }
    }

    @Value
    @Builder
    public static class MiddleConfigResult {

        TrackConfigResult publicTrack;
        TrackConfigResult privateTrack;
        TrackConfigResult intlTrack;

        public static MiddleConfigResult fromEntity(MiddleConfig entity) {
            if (entity == null) {
                return null;
            }
            return MiddleConfigResult.builder()
                    .publicTrack(TrackConfigResult.fromEntity(entity.getPublicTrack()))
                    .privateTrack(TrackConfigResult.fromEntity(entity.getPrivateTrack()))
                    .intlTrack(TrackConfigResult.fromEntity(entity.getIntlTrack()))
                    .build();
        }
    }

    @Value
    @Builder
    public static class HighConfigResult {

        TrackConfigResult publicTrack;
        TrackConfigResult privateTrack;
        TrackConfigResult intlTrack;

        public static HighConfigResult fromEntity(HighConfig entity) {
            if (entity == null) {
                return null;
            }
            return HighConfigResult.builder()
                    .publicTrack(TrackConfigResult.fromEntity(entity.getPublicTrack()))
                    .privateTrack(TrackConfigResult.fromEntity(entity.getPrivateTrack()))
                    .intlTrack(TrackConfigResult.fromEntity(entity.getIntlTrack()))
                    .build();
        }
    }

    @Value
    @Builder
    public static class UniversityConfigResult {

        TrackConfigResult jpNationalTrack;
        TrackConfigResult jpPrivateTrack;
        TrackConfigResult jpPrivateArtTrack;
        TrackConfigResult twNationalTrack;
        TrackConfigResult twPrivateTrack;
        TrackConfigResult usTrack;
        TrackConfigResult ukTrack;
        TrackConfigResult canadaTrack;
        TrackConfigResult australiaTrack;
        BigDecimal jpLivingCost;
        BigDecimal twLivingCost;
        BigDecimal usLivingCost;
        BigDecimal ukLivingCost;
        BigDecimal canadaLivingCost;
        BigDecimal australiaLivingCost;
        BigDecimal livingCost;

        public static UniversityConfigResult fromEntity(UniversityConfig entity) {
            if (entity == null) {
                return null;
            }
            return UniversityConfigResult.builder()
                    .jpNationalTrack(TrackConfigResult.fromEntity(entity.getJpNationalTrack()))
                    .jpPrivateTrack(TrackConfigResult.fromEntity(entity.getJpPrivateTrack()))
                    .jpPrivateArtTrack(TrackConfigResult.fromEntity(entity.getJpPrivateArtTrack()))
                    .twNationalTrack(TrackConfigResult.fromEntity(entity.getTwNationalTrack()))
                    .twPrivateTrack(TrackConfigResult.fromEntity(entity.getTwPrivateTrack()))
                    .usTrack(TrackConfigResult.fromEntity(entity.getUsTrack()))
                    .ukTrack(TrackConfigResult.fromEntity(entity.getUkTrack()))
                    .canadaTrack(TrackConfigResult.fromEntity(entity.getCanadaTrack()))
                    .australiaTrack(TrackConfigResult.fromEntity(entity.getAustraliaTrack()))
                    .jpLivingCost(entity.getJpLivingCost())
                    .twLivingCost(entity.getTwLivingCost())
                    .usLivingCost(entity.getUsLivingCost())
                    .ukLivingCost(entity.getUkLivingCost())
                    .canadaLivingCost(entity.getCanadaLivingCost())
                    .australiaLivingCost(entity.getAustraliaLivingCost())
                    .livingCost(entity.getLivingCost())
                    .build();
        }
    }

    public static EducationConfigResult fromEntity(EducationConfig entity) {
        if (entity == null) {
            return null;
        }
        return EducationConfigResult.builder()
                .name(entity.getName())
                .preschool(PreschoolConfigResult.fromEntity(entity.getPreschool()))
                .primary(PrimaryConfigResult.fromEntity(entity.getPrimary()))
                .middle(MiddleConfigResult.fromEntity(entity.getMiddle()))
                .high(HighConfigResult.fromEntity(entity.getHigh()))
                .university(UniversityConfigResult.fromEntity(entity.getUniversity()))
                .build();
    }
}
