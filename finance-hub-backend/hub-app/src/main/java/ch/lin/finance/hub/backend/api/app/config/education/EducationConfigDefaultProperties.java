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
package ch.lin.finance.hub.backend.api.app.config.education;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Component
@ConfigurationProperties(prefix = "finance-hub.education-config")
@Data
public class EducationConfigDefaultProperties {

    private String name = "default";

    private PreschoolConfigProperties preschool = new PreschoolConfigProperties();
    private PrimaryConfigProperties primary = new PrimaryConfigProperties();
    private MiddleConfigProperties middle = new MiddleConfigProperties();
    private HighConfigProperties high = new HighConfigProperties();
    private UniversityConfigProperties university = new UniversityConfigProperties();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrackConfigProperty {

        private BigDecimal tuition;
        private Integer duration;
        private Integer startMonth;
        @Builder.Default
        private Integer termsPerYear = 2;
        private BigDecimal entranceFee;
        private Integer standardEntranceAge;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreschoolConfigProperties {

        @Builder.Default
        private TrackConfigProperty homeTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty daycareTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty intlTrack = new TrackConfigProperty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PrimaryConfigProperties {

        @Builder.Default
        private TrackConfigProperty publicTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty privateTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty intlTrack = new TrackConfigProperty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MiddleConfigProperties {

        @Builder.Default
        private TrackConfigProperty publicTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty privateTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty intlTrack = new TrackConfigProperty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HighConfigProperties {

        @Builder.Default
        private TrackConfigProperty publicTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty privateTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty intlTrack = new TrackConfigProperty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UniversityConfigProperties {

        @Builder.Default
        private TrackConfigProperty jpNationalTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty jpPrivateTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty jpPrivateArtTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty twNationalTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty twPrivateTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty usTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty ukTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty canadaTrack = new TrackConfigProperty();
        @Builder.Default
        private TrackConfigProperty australiaTrack = new TrackConfigProperty();
        private BigDecimal jpLivingCost;
        private BigDecimal twLivingCost;
        private BigDecimal usLivingCost;
        private BigDecimal ukLivingCost;
        private BigDecimal canadaLivingCost;
        private BigDecimal australiaLivingCost;
        private BigDecimal livingCost;
    }
}
