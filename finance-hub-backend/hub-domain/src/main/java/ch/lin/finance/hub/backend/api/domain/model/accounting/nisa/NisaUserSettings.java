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
package ch.lin.finance.hub.backend.api.domain.model.accounting.nisa;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nisa_user_settings")
@Getter
@Setter
@NoArgsConstructor
public class NisaUserSettings extends MultiUserBaseEntity {

    @Column(name = "monthly_growth_contribution", nullable = false, columnDefinition = "bigint default 0")
    @NotNull
    @Min(0)
    @Max(200000)
    private Long monthlyGrowthContribution = 0L;

    @Column(name = "monthly_tsumitate_contribution", nullable = false, columnDefinition = "bigint default 0")
    @NotNull
    @Min(0)
    @Max(100000)
    private Long monthlyTsumitateContribution = 0L;

    @Column(name = "nisa_expected_return_rate")
    private Double nisaExpectedReturnRate = 0.05;
}
