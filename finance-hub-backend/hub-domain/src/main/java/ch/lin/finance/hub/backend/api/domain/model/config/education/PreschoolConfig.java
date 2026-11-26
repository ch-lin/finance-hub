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
package ch.lin.finance.hub.backend.api.domain.model.config.education;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Preschool / Kindergarten tracks.
 *
 * <p>Baseline parameters are loaded from application properties (e.g., {@code application.properties})
 * and can be overridden per child profile.</p>
 *
 * <p><b>Important Unit Conventions:</b></p>
 * <ul>
 *   <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
 *   <li>{@code duration} represents total study duration strictly in <b>Months</b> (e.g., 36 months).</li>
 * </ul>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreschoolConfig {

    /** Home care / homeschool track (e.g., 36 months, 0 tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tuition", column = @Column(name = "preschool_home_tuition", precision = 19, scale = 4)),
            @AttributeOverride(name = "duration", column = @Column(name = "preschool_home_duration")),
            @AttributeOverride(name = "startMonth", column = @Column(name = "preschool_home_start_month")),
            @AttributeOverride(name = "termsPerYear", column = @Column(name = "preschool_home_terms_per_year")),
            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_home_entrance_fee", precision = 19, scale = 4)),
            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "preschool_home_standard_entrance_age"))
    })
    private TrackConfig homeTrack;

    /** Daycare track (e.g., 36 months, ~300,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tuition", column = @Column(name = "preschool_daycare_tuition", precision = 19, scale = 4)),
            @AttributeOverride(name = "duration", column = @Column(name = "preschool_daycare_duration")),
            @AttributeOverride(name = "startMonth", column = @Column(name = "preschool_daycare_start_month")),
            @AttributeOverride(name = "termsPerYear", column = @Column(name = "preschool_daycare_terms_per_year")),
            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_daycare_entrance_fee", precision = 19, scale = 4)),
            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "preschool_daycare_standard_entrance_age"))
    })
    private TrackConfig daycareTrack;

    /** International preschool track (e.g., 36 months, ~1,800,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tuition", column = @Column(name = "preschool_intl_tuition", precision = 19, scale = 4)),
            @AttributeOverride(name = "duration", column = @Column(name = "preschool_intl_duration")),
            @AttributeOverride(name = "startMonth", column = @Column(name = "preschool_intl_start_month")),
            @AttributeOverride(name = "termsPerYear", column = @Column(name = "preschool_intl_terms_per_year")),
            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_intl_entrance_fee", precision = 19, scale = 4)),
            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "preschool_intl_standard_entrance_age"))
    })
    private TrackConfig intlTrack;
}
