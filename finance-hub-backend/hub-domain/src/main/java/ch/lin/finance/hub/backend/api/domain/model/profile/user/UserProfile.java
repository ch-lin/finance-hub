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
package ch.lin.finance.hub.backend.api.domain.model.profile.user;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the financial profile and background configuration for a specific
 * user. The ID of this entity corresponds directly to the user's ID in the
 * Authentication Service.
 */
@Entity
@Table(name = UserProfile.TABLE_NAME)
@Getter
@Setter
@EqualsAndHashCode(of = { "id" })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    public static final String TABLE_NAME = "finance_user_profile";
    public static final String ID_COLUMN = "id";
    public static final String BIRTH_DATE_COLUMN = "birth_date";
    public static final String MANUAL_MONTHLY_BASE_EXPENSE_COLUMN = "manual_monthly_base_expense";
    public static final String TARGET_RETIREMENT_AGE_COLUMN = "target_retirement_age";
    public static final String PENSION_START_AGE_COLUMN = "pension_start_age";
    public static final String DISPLAY_NAME_COLUMN = "display_name";
    public static final String DEFAULT_CURRENCY_COLUMN = "default_currency";

    public static final String LIFE_EXPECTANCY_COLUMN = "life_expectancy";
    public static final String TAX_ADVANTAGED_WITHDRAWAL_AGE_COLUMN = "tax_advantaged_withdrawal_age";
    public static final String APPLY_PROFILE_TO_OPTIMIZER_BASELINE_COLUMN = "apply_profile_to_optimizer_baseline";

    /**
     * The ID of the user (mapped directly from the Authentication Service).
     * This is manually assigned, not auto-generated.
     */
    @Id
    @Column(name = UserProfile.ID_COLUMN, length = 36, nullable = false)
    private String id;

    /**
     * The user's birth date, used to dynamically calculate current age for
     * simulations.
     */
    @Column(name = UserProfile.BIRTH_DATE_COLUMN, nullable = false)
    private LocalDate birthDate;

    /**
     * An optional manual override for the user's estimated monthly living expense.
     * Used as a fallback if dynamic transaction-based calculation fails.
     */
    @Column(name = UserProfile.MANUAL_MONTHLY_BASE_EXPENSE_COLUMN)
    private Long manualMonthlyBaseExpense;

    /**
     * The user's target retirement age (default can be 65).
     */
    @Column(name = UserProfile.TARGET_RETIREMENT_AGE_COLUMN)
    private Integer targetRetirementAge;

    /**
     * The user's pension eligible age override (default can be null, fallback to
     * config).
     */
    @Column(name = UserProfile.PENSION_START_AGE_COLUMN)
    private Integer pensionStartAge;

    /**
     * The user's chosen display name for the finance hub UI (e.g. for family
     * sharing).
     */
    @Column(name = UserProfile.DISPLAY_NAME_COLUMN)
    private String displayName;

    /**
     * The user's custom preferred currency.
     * If null, the system-wide default currency will be used.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = UserProfile.DEFAULT_CURRENCY_COLUMN, length = 10)
    private CurrencyCode defaultCurrency;

    /**
     * The age at which the user plans to withdraw their tax-advantaged accounts
     * (iDeCo and Corporate DC).
     * Defaults to null (will be resolved to the statutory unlock age).
     */
    @Column(name = UserProfile.TAX_ADVANTAGED_WITHDRAWAL_AGE_COLUMN)
    private Integer taxAdvantagedWithdrawalAge;

    /**
     * The user's life expectancy.
     */
    @Column(name = UserProfile.LIFE_EXPECTANCY_COLUMN)
    private Integer lifeExpectancy;

    /**
     * Whether the user's profile settings should be used as the baseline for
     * unselected dimensions during AI optimization.
     */
    @Column(name = UserProfile.APPLY_PROFILE_TO_OPTIMIZER_BASELINE_COLUMN)
    private boolean applyProfileToOptimizerBaseline = true;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "user_profile_id")
    private List<Dependent> familyMembers = new ArrayList<>();

    public UserProfile(String id, LocalDate birthDate, Long manualMonthlyBaseExpense, Integer targetRetirementAge) {
        this.id = id;
        this.birthDate = birthDate;
        this.manualMonthlyBaseExpense = manualMonthlyBaseExpense;
        this.targetRetirementAge = targetRetirementAge;
    }
}
