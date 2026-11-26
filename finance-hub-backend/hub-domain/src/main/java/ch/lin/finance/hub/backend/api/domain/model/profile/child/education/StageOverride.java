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
package ch.lin.finance.hub.backend.api.domain.model.profile.child.education;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Value object representing user-customized overrides for an educational stage.
 *
 * <p>When fields are null, the simulation falls back to baseline values
 * configured in {@link EducationConfig}.</p>
 *
 * <p><b>Important Unit Conventions:</b></p>
 * <ul>
 *   <li><b>manualTuition:</b> User-specified <b>Annual Tuition</b> in JPY (e.g., 1,500,000 JPY).
 *       Overrides the baseline track annual tuition.</li>
 *   <li><b>manualDuration:</b> Total study duration strictly in <b>Months</b> (e.g., 36 months for 3 years;
 *       must NOT be set as number of years).</li>
 *   <li><b>manualStartMonth:</b> Entrance calendar month (1–12, e.g., 4 for April, 9 for September).</li>
 *   <li><b>manualTermsPerYear:</b> Payment installments per academic year (e.g., 2).</li>
 * </ul>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StageOverride {

    /**
     * User-specified annual tuition in JPY (e.g., 1,500,000 JPY).
     * If null, falls back to the baseline track annual tuition from configuration properties.
     */
    @Column(name = "manual_tuition", precision = 19, scale = 4)
    private BigDecimal manualTuition;

    /**
     * User-specified study duration strictly in MONTHS (e.g., 36 for 3 years, 72 for 6 years; NOT in years).
     * If null, falls back to the baseline track duration from configuration properties.
     */
    @Column(name = "manual_duration")
    private Integer manualDuration;

    /**
     * User-specified entrance calendar month (1–12, e.g., 4 for April, 9 for September).
     * If null, falls back to the baseline track start month.
     */
    @Column(name = "manual_start_month")
    private Integer manualStartMonth;

    /**
     * User-specified payment installments per academic year (e.g., 2).
     * If null, falls back to the baseline track termsPerYear.
     */
    @Column(name = "manual_terms_per_year")
    private Integer manualTermsPerYear;
}
