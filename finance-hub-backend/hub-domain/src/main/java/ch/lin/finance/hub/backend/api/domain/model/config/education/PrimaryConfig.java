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
 * Configuration for Primary / Elementary School tracks.
 *
 * <p>Baseline parameters are loaded from application properties (e.g., {@code application.properties})
 * and can be overridden per child profile.</p>
 *
 * <p><b>Important Unit Conventions:</b></p>
 * <ul>
 *   <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
 *   <li>{@code duration} represents total study duration strictly in <b>Months</b> (e.g., 72 months for 6-year primary education).</li>
 * </ul>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrimaryConfig {

    /** Public elementary school track (e.g., 72 months, ~100,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tuition", column = @Column(name = "primary_public_tuition", precision = 19, scale = 4)),
            @AttributeOverride(name = "duration", column = @Column(name = "primary_public_duration")),
            @AttributeOverride(name = "startMonth", column = @Column(name = "primary_public_start_month")),
            @AttributeOverride(name = "termsPerYear", column = @Column(name = "primary_public_terms_per_year")),
            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_public_entrance_fee", precision = 19, scale = 4)),
            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "primary_public_standard_entrance_age"))
    })
    private TrackConfig publicTrack;

    /** Private elementary school track (e.g., 72 months, ~1,000,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tuition", column = @Column(name = "primary_private_tuition", precision = 19, scale = 4)),
            @AttributeOverride(name = "duration", column = @Column(name = "primary_private_duration")),
            @AttributeOverride(name = "startMonth", column = @Column(name = "primary_private_start_month")),
            @AttributeOverride(name = "termsPerYear", column = @Column(name = "primary_private_terms_per_year")),
            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_private_entrance_fee", precision = 19, scale = 4)),
            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "primary_private_standard_entrance_age"))
    })
    private TrackConfig privateTrack;

    /** International primary school track (e.g., 72 months, ~2,500,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tuition", column = @Column(name = "primary_intl_tuition", precision = 19, scale = 4)),
            @AttributeOverride(name = "duration", column = @Column(name = "primary_intl_duration")),
            @AttributeOverride(name = "startMonth", column = @Column(name = "primary_intl_start_month")),
            @AttributeOverride(name = "termsPerYear", column = @Column(name = "primary_intl_terms_per_year")),
            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_intl_entrance_fee", precision = 19, scale = 4)),
            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "primary_intl_standard_entrance_age"))
    })
    private TrackConfig intlTrack;
}
