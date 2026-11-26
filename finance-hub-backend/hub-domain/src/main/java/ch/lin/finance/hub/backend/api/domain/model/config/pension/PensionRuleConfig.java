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
package ch.lin.finance.hub.backend.api.domain.model.config.pension;

import ch.lin.platform.domain.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = PensionRuleConfig.TABLE_NAME, uniqueConstraints = {
        @UniqueConstraint(name = PensionRuleConfig.UNIQUE_NAME, columnNames = PensionRuleConfig.NAME_COLUMN)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = { "name", "enabled" }, callSuper = false)
public class PensionRuleConfig extends AuditableEntity {

    public static final String TABLE_NAME = "pension_rule_configs";
    public static final String UNIQUE_NAME = "uk_pension_rule_config_name";
    public static final String NAME_COLUMN = "name";

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "max_basic_pension_amount", nullable = false)
    private Long maxBasicPensionAmount;

    @Column(name = "max_basic_pension_months", nullable = false)
    private Long maxBasicPensionMonths;

    @Column(name = "kousei_multiplier_per_mille", nullable = false, precision = 6, scale = 3)
    private java.math.BigDecimal kouseiMultiplierPerMille;

    @Column(name = "kousei_salary_cap_monthly", nullable = false)
    private Long kouseiSalaryCapMonthly;

    @Column(name = "kousei_bonus_cap_per_payment", nullable = false)
    private Long kouseiBonusCapPerPayment;

    public PensionRuleConfig(String name, Boolean enabled, Long maxBasicPensionAmount, Long maxBasicPensionMonths,
            java.math.BigDecimal kouseiMultiplierPerMille, Long kouseiSalaryCapMonthly,
            Long kouseiBonusCapPerPayment) {
        this.name = name;
        this.enabled = enabled;
        this.maxBasicPensionAmount = maxBasicPensionAmount;
        this.maxBasicPensionMonths = maxBasicPensionMonths;
        this.kouseiMultiplierPerMille = kouseiMultiplierPerMille;
        this.kouseiSalaryCapMonthly = kouseiSalaryCapMonthly;
        this.kouseiBonusCapPerPayment = kouseiBonusCapPerPayment;
    }
}
