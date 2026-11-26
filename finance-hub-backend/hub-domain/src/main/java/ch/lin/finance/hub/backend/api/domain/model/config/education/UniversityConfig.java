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

import java.math.BigDecimal;

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
 * Configuration for University / Higher Education tracks and baseline living costs.
 *
 * <p>Baseline parameters are loaded from application properties (e.g., {@code application.properties})
 * and can be overridden per child profile.</p>
 *
 * <p><b>Important Unit Conventions:</b></p>
 * <ul>
 *   <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
 *   <li>{@code duration} represents total study duration strictly in <b>Months</b> (e.g., 36 or 48 months).</li>
 *   <li>{@code livingCost} represents baseline <b>Monthly Living Cost</b> in JPY for overseas or away-from-home students.</li>
 * </ul>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityConfig {

    /** Japan National University track (e.g., 48 months, ~500,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_jp_national_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_jp_national_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_jp_national_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_jp_national_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_national_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_jp_national_standard_entrance_age"))
    })
    private TrackConfig jpNationalTrack;

    /** Japan Private University track (e.g., 48 months, ~1,000,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_jp_private_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_jp_private_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_jp_private_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_jp_private_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_private_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_jp_private_standard_entrance_age"))
    })
    private TrackConfig jpPrivateTrack;

    /** Japan Private Art/Medical University track (e.g., 48 months, ~1,800,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_jp_private_art_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_jp_private_art_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_jp_private_art_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_jp_private_art_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_private_art_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_jp_private_art_standard_entrance_age"))
    })
    private TrackConfig jpPrivateArtTrack;

    /** Taiwan National University track (e.g., 48 months, ~250,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_tw_national_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_tw_national_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_tw_national_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_tw_national_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_tw_national_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_tw_national_standard_entrance_age"))
    })
    private TrackConfig twNationalTrack;

    /** Taiwan Private University track (e.g., 48 months, ~500,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_tw_private_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_tw_private_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_tw_private_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_tw_private_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_tw_private_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_tw_private_standard_entrance_age"))
    })
    private TrackConfig twPrivateTrack;

    /** United States University track (e.g., 48 months, ~6,000,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_us_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_us_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_us_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_us_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_us_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_us_standard_entrance_age"))
    })
    private TrackConfig usTrack;

    /** United Kingdom University track (e.g., 36 months, ~4,500,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_uk_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_uk_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_uk_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_uk_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_uk_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_uk_standard_entrance_age"))
    })
    private TrackConfig ukTrack;

    /** Canada University track (e.g., 48 months, ~3,500,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_canada_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_canada_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_canada_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_canada_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_canada_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_canada_standard_entrance_age"))
    })
    private TrackConfig canadaTrack;

    /** Australia University track (e.g., 36 months, ~4,000,000 JPY/year tuition; configured via properties). */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tuition", column = @Column(name = "uni_australia_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "duration", column = @Column(name = "uni_australia_duration")),
        @AttributeOverride(name = "startMonth", column = @Column(name = "uni_australia_start_month")),
        @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_australia_terms_per_year")),
        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_australia_entrance_fee", precision = 19, scale = 4)),
        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_australia_standard_entrance_age"))
    })
    private TrackConfig australiaTrack;

    /**
     * Standard baseline living cost in JPY per MONTH for students studying in Japan (away from home).
     */
    @Column(name = "uni_jp_living_cost", precision = 19, scale = 4)
    private BigDecimal jpLivingCost;

    /**
     * Standard baseline living cost in JPY per MONTH for students studying in Taiwan (away from home).
     */
    @Column(name = "uni_tw_living_cost", precision = 19, scale = 4)
    private BigDecimal twLivingCost;

    /**
     * Standard baseline living cost in JPY per MONTH for students studying abroad in the United States.
     */
    @Column(name = "uni_us_living_cost", precision = 19, scale = 4)
    private BigDecimal usLivingCost;

    /**
     * Standard baseline living cost in JPY per MONTH for students studying abroad in the United Kingdom.
     */
    @Column(name = "uni_uk_living_cost", precision = 19, scale = 4)
    private BigDecimal ukLivingCost;

    /**
     * Standard baseline living cost in JPY per MONTH for students studying abroad in Canada.
     */
    @Column(name = "uni_canada_living_cost", precision = 19, scale = 4)
    private BigDecimal canadaLivingCost;

    /**
     * Standard baseline living cost in JPY per MONTH for students studying abroad in Australia.
     */
    @Column(name = "uni_australia_living_cost", precision = 19, scale = 4)
    private BigDecimal australiaLivingCost;

    /**
     * Standard baseline living cost in JPY per MONTH for students studying abroad or living away from home
     * (generic fallback e.g., ~125,000 JPY/month; configured via application properties and stored on a monthly basis).
     */
    @Column(name = "uni_living_cost", precision = 19, scale = 4)
    private BigDecimal livingCost;
}
