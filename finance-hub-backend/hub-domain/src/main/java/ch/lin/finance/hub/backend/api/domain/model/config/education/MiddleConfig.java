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
 * Configuration for Middle / Junior High School tracks.
 *
 * <p>
 * Baseline parameters are loaded from application properties (e.g.,
 * {@code application.properties}) and can be overridden per child profile.</p>
 *
 * <p>
 * <b>Important Unit Conventions:</b></p>
 * <ul>
 * <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
 * <li>{@code duration} represents total study duration strictly in
 * <b>Months</b> (e.g., 36 months for 3-year middle school).</li>
 * </ul>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MiddleConfig {

    /**
     * Public middle school track (e.g., 36 months, ~150,000 JPY/year tuition;
     * configured via properties).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "middle_public_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "middle_public_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "middle_public_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_public_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_public_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "middle_public_standard_entrance_age"))
    })
    private TrackConfig publicTrack;

    /**
     * Private middle school track (e.g., 36 months, ~1,100,000 JPY/year
     * tuition; configured via properties).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "middle_private_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "middle_private_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "middle_private_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_private_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_private_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "middle_private_standard_entrance_age"))
    })
    private TrackConfig privateTrack;

    /**
     * International middle school track (e.g., 36 months, ~2,800,000 JPY/year
     * tuition; configured via properties).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "middle_intl_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "middle_intl_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "middle_intl_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_intl_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_intl_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "middle_intl_standard_entrance_age"))
    })
    private TrackConfig intlTrack;
}
