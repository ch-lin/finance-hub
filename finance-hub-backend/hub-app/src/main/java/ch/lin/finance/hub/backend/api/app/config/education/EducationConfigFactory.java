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

import java.util.Objects;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.HighConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.MiddleConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.PreschoolConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.PrimaryConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.TrackConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.UniversityConfig;

@Component
public class EducationConfigFactory {

    public EducationConfig create(EducationConfigDefaultProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        return new EducationConfig(
                Objects.requireNonNull(properties.getName(), "properties.name must not be null"),
                createPreschoolConfig(properties.getPreschool()),
                createPrimaryConfig(properties.getPrimary()),
                createMiddleConfig(properties.getMiddle()),
                createHighConfig(properties.getHigh()),
                createUniversityConfig(properties.getUniversity()));
    }

    private PreschoolConfig createPreschoolConfig(EducationConfigDefaultProperties.PreschoolConfigProperties props) {
        if (props == null) {
            return null;
        }
        return PreschoolConfig.builder()
                .homeTrack(createTrackConfig(props.getHomeTrack()))
                .daycareTrack(createTrackConfig(props.getDaycareTrack()))
                .intlTrack(createTrackConfig(props.getIntlTrack()))
                .build();
    }

    private PrimaryConfig createPrimaryConfig(EducationConfigDefaultProperties.PrimaryConfigProperties props) {
        if (props == null) {
            return null;
        }
        return PrimaryConfig.builder()
                .publicTrack(createTrackConfig(props.getPublicTrack()))
                .privateTrack(createTrackConfig(props.getPrivateTrack()))
                .intlTrack(createTrackConfig(props.getIntlTrack()))
                .build();
    }

    private MiddleConfig createMiddleConfig(EducationConfigDefaultProperties.MiddleConfigProperties props) {
        if (props == null) {
            return null;
        }
        return MiddleConfig.builder()
                .publicTrack(createTrackConfig(props.getPublicTrack()))
                .privateTrack(createTrackConfig(props.getPrivateTrack()))
                .intlTrack(createTrackConfig(props.getIntlTrack()))
                .build();
    }

    private HighConfig createHighConfig(EducationConfigDefaultProperties.HighConfigProperties props) {
        if (props == null) {
            return null;
        }
        return HighConfig.builder()
                .publicTrack(createTrackConfig(props.getPublicTrack()))
                .privateTrack(createTrackConfig(props.getPrivateTrack()))
                .intlTrack(createTrackConfig(props.getIntlTrack()))
                .build();
    }

    private UniversityConfig createUniversityConfig(EducationConfigDefaultProperties.UniversityConfigProperties props) {
        if (props == null) {
            return null;
        }
        return UniversityConfig.builder()
                .jpNationalTrack(createTrackConfig(props.getJpNationalTrack()))
                .jpPrivateTrack(createTrackConfig(props.getJpPrivateTrack()))
                .jpPrivateArtTrack(createTrackConfig(props.getJpPrivateArtTrack()))
                .twNationalTrack(createTrackConfig(props.getTwNationalTrack()))
                .twPrivateTrack(createTrackConfig(props.getTwPrivateTrack()))
                .usTrack(createTrackConfig(props.getUsTrack()))
                .ukTrack(createTrackConfig(props.getUkTrack()))
                .canadaTrack(createTrackConfig(props.getCanadaTrack()))
                .australiaTrack(createTrackConfig(props.getAustraliaTrack()))
                .jpLivingCost(props.getJpLivingCost())
                .twLivingCost(props.getTwLivingCost())
                .usLivingCost(props.getUsLivingCost())
                .ukLivingCost(props.getUkLivingCost())
                .canadaLivingCost(props.getCanadaLivingCost())
                .australiaLivingCost(props.getAustraliaLivingCost())
                .livingCost(props.getLivingCost())
                .build();
    }

    private TrackConfig createTrackConfig(EducationConfigDefaultProperties.TrackConfigProperty props) {
        if (props == null) {
            return null;
        }
        return TrackConfig.builder()
                .tuition(props.getTuition())
                .duration(props.getDuration())
                .startMonth(props.getStartMonth())
                .termsPerYear(props.getTermsPerYear())
                .entranceFee(props.getEntranceFee())
                .standardEntranceAge(props.getStandardEntranceAge())
                .build();
    }
}
