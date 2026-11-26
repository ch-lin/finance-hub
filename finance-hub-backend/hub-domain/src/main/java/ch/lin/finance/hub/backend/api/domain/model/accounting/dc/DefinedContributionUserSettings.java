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
package ch.lin.finance.hub.backend.api.domain.model.accounting.dc;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dc_user_settings")
@Check(constraints = "(is_dc_contribution_years_overridden = false OR dc_contribution_years_override IS NOT NULL) AND (is_ideco_contribution_years_overridden = false OR ideco_contribution_years_override IS NOT NULL)")
@Getter
@Setter
@NoArgsConstructor
public class DefinedContributionUserSettings extends MultiUserBaseEntity {

    @Column(name = "is_dc_contribution_years_overridden")
    private Boolean isDcContributionYearsOverridden = false;

    @Column(name = "dc_contribution_years_override")
    private Integer dcContributionYearsOverride;

    @Column(name = "is_ideco_contribution_years_overridden")
    private Boolean isIdecoContributionYearsOverridden = false;

    @Column(name = "ideco_contribution_years_override")
    private Integer idecoContributionYearsOverride;

    @Column(name = "monthly_ideco_contribution")
    private Long monthlyIdecoContribution = 0L;

    @Column(name = "ideco_expected_return_rate")
    private Double idecoExpectedReturnRate = 0.04;

    @Column(name = "monthly_dc_matching_contribution")
    private Long monthlyDcMatchingContribution = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dc_target_allocation", columnDefinition = "json")
    private DcTargetAllocationSetting dcTargetAllocation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ideco_target_allocation", columnDefinition = "json")
    private DcTargetAllocationSetting idecoTargetAllocation;

    public DcTargetAllocationSetting getTargetAllocation(DefinedContributionType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case CORPORATE_DC -> this.dcTargetAllocation;
            case IDECO -> this.idecoTargetAllocation;
        };
    }

    public void updateTargetAllocation(DefinedContributionType type, DcTargetAllocationSetting setting) {
        if (type == null) {
            throw new IllegalArgumentException("DefinedContributionType must not be null");
        }
        switch (type) {
            case CORPORATE_DC -> this.dcTargetAllocation = setting;
            case IDECO -> this.idecoTargetAllocation = setting;
        }
    }

}
