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

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Baseline parameters for an educational track (e.g., Public, Private, International).
 *
 * <p>Values are populated from application properties (e.g., {@code application.properties}
 * or environment variables) and can be overridden per child profile.</p>
 *
 * <p><b>Important Unit Conventions:</b></p>
 * <ul>
 *   <li><b>tuition:</b> Baseline <b>Annual Tuition</b> in JPY (e.g., ~2,500,000 JPY for international primary,
 *       ~500,000 JPY for national university). In the timeline projector, this annual figure is divided
 *       by {@code termsPerYear} across the specified payment months.</li>
 *   <li><b>duration:</b> Total study duration strictly in <b>Months</b> (e.g., 36 months for a 3-year track,
 *       72 months for a 6-year track).</li>
 *   <li><b>startMonth:</b> Starting calendar month of the academic year (1–12, e.g., 4 for April, 9 for September).</li>
 *   <li><b>termsPerYear:</b> Number of payment installments per academic year (e.g., 2 for semester-based payments).</li>
 *   <li><b>entranceFee:</b> One-time admission fee in JPY charged in the starting month of enrollment.</li>
 * </ul>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackConfig {

    /**
     * Standard baseline annual tuition in JPY (e.g., ~2,500,000 JPY for international primary,
     * ~500,000 JPY for national university; exact values are configured via application properties).
     * This represents the full academic year tuition, which is divided by {@code termsPerYear}
     * into periodic payment installments in {@code EducationTimelineProjector}.
     */
    @Column(name = "tuition", precision = 19, scale = 4)
    private BigDecimal tuition;

    /**
     * Standard study duration of this track strictly in MONTHS (e.g., 36 months for a 3-year track,
     * 72 months for a 6-year track; configured via application properties).
     */
    @Column(name = "duration")
    private Integer duration;

    /**
     * Entrance start calendar month (1–12, e.g., 4 for April in Japan, 9 for September in US/UK/TW).
     */
    @Column(name = "start_month")
    private Integer startMonth;

    /**
     * Default number of payment installments per academic year (e.g., 2 for semester-based tuition).
     */
    @Column(name = "terms_per_year")
    @Builder.Default
    private Integer termsPerYear = 2;

    /**
     * One-time entrance admission fee in JPY, charged once in the starting month of enrollment.
     */
    @Column(name = "entrance_fee", precision = 19, scale = 4)
    private BigDecimal entranceFee;

    /**
     * Standard statutory or curriculum entrance age in years (e.g., 3 for preschool, 6 for primary,
     * 12 for middle, 15 for high, 18 for university; configured via application properties).
     */
    @Column(name = "standard_entrance_age")
    private Integer standardEntranceAge;
}

