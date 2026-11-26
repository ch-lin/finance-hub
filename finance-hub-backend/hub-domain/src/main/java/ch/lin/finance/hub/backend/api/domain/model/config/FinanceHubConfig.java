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
package ch.lin.finance.hub.backend.api.domain.model.config;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.platform.domain.model.AuditableEntity;
import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a named configuration profile for the finance hub.
 */
@Entity
@Table(name = FinanceHubConfig.TABLE_NAME, indexes = {
        @Index(name = FinanceHubConfig.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(name = FinanceHubConfig.UNIQUE_NAME, columnNames = FinanceHubConfig.NAME_COLUMN)
})
@Getter
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation. However, from a Domain-Driven Design (DDD) perspective, an
// entity should never be created in an invalid state without its required
// fields. Therefore, we use AccessLevel.PROTECTED to satisfy JPA/Hibernate
// requirements while preventing arbitrary instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = { "name", "enabled", "fileProcessingTimeoutSeconds" }, callSuper = false)
public class FinanceHubConfig extends AuditableEntity {

    /**
     * The name of the finance hub config table in the database.
     */
    public static final String TABLE_NAME = "finance_hub_config";

    public static final String ID_INDEX = "finance_hub_config_id_index";
    public static final String UNIQUE_NAME = "uk_finance_hub_config_name";

    /**
     * The name of the name column in the database.
     */
    public static final String NAME_COLUMN = "name";

    /**
     * The name of the enabled column in the database.
     */
    public static final String ENABLED_COLUMN = "enabled";

    /**
     * The name of the file processing timeout seconds column in the database.
     */
    public static final String FILE_PROCESSING_TIMEOUT_SECONDS_COLUMN = "file_processing_timeout_seconds";

    /**
     * The name of the NISA expected return rate column in the database.
     */
    public static final String NISA_EXPECTED_RETURN_RATE_COLUMN = "nisa_expected_return_rate";
    public static final String TARGET_RETIREMENT_AGE_COLUMN = "target_retirement_age";
    public static final String LIFE_EXPECTANCY_COLUMN = "life_expectancy";

    /**
     * The name of the iDeCo expected return rate column in the database.
     */
    public static final String IDECO_EXPECTED_RETURN_RATE_COLUMN = "ideco_expected_return_rate";

    /**
     * The name of the pension expected monthly salary column in the database.
     */
    public static final String PENSION_EXPECTED_MONTHLY_SALARY_COLUMN = "pension_expected_monthly_salary";

    /**
     * The name of the expense calculation past months column in the database.
     */
    public static final String EXPENSE_CALCULATION_PAST_MONTHS_COLUMN = "expense_calculation_past_months";

    /**
     * The name of the ideco unlock age column in the database.
     */
    public static final String IDECO_UNLOCK_AGE_COLUMN = "ideco_unlock_age";

    /**
     * The name of the dc unlock age column in the database.
     */
    public static final String DC_UNLOCK_AGE_COLUMN = "dc_unlock_age";

    /**
     * The name of the inflation rate column in the database.
     */
    public static final String INFLATION_RATE_COLUMN = "inflation_rate";

    /**
     * The name of the pension eligible age column in the database.
     */
    public static final String PENSION_START_AGE_COLUMN = "pension_start_age";

    public static final String GA_POPULATION_SIZE_COLUMN = "ga_population_size";
    public static final String GA_MAX_GENERATIONS_COLUMN = "ga_max_generations";
    public static final String GA_MUTATION_RATE_COLUMN = "ga_mutation_rate";
    public static final String GA_ELITE_RATIO_COLUMN = "ga_elite_ratio";
    public static final String SIMULATION_EARLY_REPAYMENT_MONTH_COLUMN = "simulation_early_repayment_month";
    public static final String RETIREMENT_MONTH_MODE_COLUMN = "retirement_month_mode";
    public static final String DEFAULT_CURRENCY_COLUMN = "default_currency";

    /**
     * The unique name for this configuration profile.
     */
    @NotNull
    @Column(name = FinanceHubConfig.NAME_COLUMN, nullable = false)
    private String name;

    /**
     * The system-wide default currency.
     */
    @NotNull
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @ColumnDefault("'JPY'")
    @Column(name = FinanceHubConfig.DEFAULT_CURRENCY_COLUMN, nullable = false, length = 10)
    @Setter
    private CurrencyCode defaultCurrency = CurrencyCode.JPY;

    /**
     * A flag to indicate whether this configuration profile is active.
     */
    @NotNull
    @ColumnDefault("false")
    @Column(name = FinanceHubConfig.ENABLED_COLUMN, nullable = false)
    @Setter
    private Boolean enabled = false;

    /**
     * The file processing timeout in seconds.
     */
    @NotNull
    @ColumnDefault("10")
    @Column(name = FinanceHubConfig.FILE_PROCESSING_TIMEOUT_SECONDS_COLUMN, nullable = false)
    @Setter
    private Integer fileProcessingTimeoutSeconds = 10;

    /**
     * Expected annual return rate for NISA (e.g., 0.05 for 5%).
     */
    @NotNull
    @ColumnDefault("0.05")
    @Column(name = FinanceHubConfig.NISA_EXPECTED_RETURN_RATE_COLUMN, nullable = false)
    @Setter
    private Double nisaExpectedReturnRate = 0.05;

    /**
     * Expected annual return rate for iDeCo / Corporate DC (e.g., 0.04 for 4%).
     */
    @NotNull
    @ColumnDefault("0.04")
    @Column(name = FinanceHubConfig.IDECO_EXPECTED_RETURN_RATE_COLUMN, nullable = false)
    @Setter
    private Double idecoExpectedReturnRate = 0.04;

    /**
     * Expected average monthly salary for pension projection (in JPY). Used as
     * a temporary measure until Phase 4's CareerIncomeTimeline.
     */
    @NotNull
    @ColumnDefault("500000")
    @Column(name = FinanceHubConfig.PENSION_EXPECTED_MONTHLY_SALARY_COLUMN, nullable = false)
    @Setter
    private Long pensionExpectedMonthlySalary = 500000L;

    /**
     * The number of past months to look back when dynamically calculating
     * baseline annual expenses.
     */
    @NotNull
    @ColumnDefault("12")
    @Column(name = FinanceHubConfig.EXPENSE_CALCULATION_PAST_MONTHS_COLUMN, nullable = false)
    @Setter
    private Integer expenseCalculationPastMonths = 12;

    /**
     * The age at which iDeCo is unlocked.
     */
    @NotNull
    @ColumnDefault("60")
    @Column(name = FinanceHubConfig.IDECO_UNLOCK_AGE_COLUMN, nullable = false)
    @Setter
    private Integer idecoUnlockAge = 60;

    /**
     * The age at which Corporate DC is unlocked.
     */
    @NotNull
    @ColumnDefault("60")
    @Column(name = FinanceHubConfig.DC_UNLOCK_AGE_COLUMN, nullable = false)
    @Setter
    private Integer dcUnlockAge = 60;

    /**
     * The target retirement age.
     */
    @NotNull
    @ColumnDefault("65")
    @Column(name = FinanceHubConfig.TARGET_RETIREMENT_AGE_COLUMN, nullable = false)
    @Setter
    private Integer targetRetirementAge = 65;

    /**
     * The standard pension eligible age.
     */
    @NotNull
    @ColumnDefault("65")
    @Column(name = FinanceHubConfig.PENSION_START_AGE_COLUMN, nullable = false)
    @Setter
    private Integer pensionStartAge = 65;

    /**
     * The life expectancy.
     */
    @NotNull
    @ColumnDefault("85")
    @Column(name = FinanceHubConfig.LIFE_EXPECTANCY_COLUMN, nullable = false)
    @Setter
    private Integer lifeExpectancy = 85;

    /**
     * Expected annual inflation rate (e.g., 0.02 for 2%).
     */
    @NotNull
    @ColumnDefault("0.0")
    @Column(name = FinanceHubConfig.INFLATION_RATE_COLUMN, nullable = false)
    @Setter
    private Double inflationRate = 0.0;

    @NotNull
    @ColumnDefault("50")
    @Column(name = FinanceHubConfig.GA_POPULATION_SIZE_COLUMN, nullable = false)
    @Setter
    private Integer gaPopulationSize = 50;

    @NotNull
    @ColumnDefault("20")
    @Column(name = FinanceHubConfig.GA_MAX_GENERATIONS_COLUMN, nullable = false)
    @Setter
    private Integer gaMaxGenerations = 20;

    @NotNull
    @ColumnDefault("0.2")
    @Column(name = FinanceHubConfig.GA_MUTATION_RATE_COLUMN, nullable = false)
    @Setter
    private Double gaMutationRate = 0.2;

    @NotNull
    @Min(value = 1)
    @Max(value = 50)
    @ColumnDefault("10")
    @Column(name = FinanceHubConfig.GA_ELITE_RATIO_COLUMN, nullable = false)
    @Setter
    private Integer gaEliteRatio = 10;

    /**
     * The default month for simulation early repayment (1-12).
     */
    @NotNull
    @Min(value = 1)
    @Max(value = 12)
    @ColumnDefault("12")
    @Column(name = FinanceHubConfig.SIMULATION_EARLY_REPAYMENT_MONTH_COLUMN, nullable = false)
    @Setter
    private Integer simulationEarlyRepaymentMonth = 12;

    /**
     * Determines which month retirement takes effect in: the December of the
     * target retirement age's year, or that age's birth month.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DECEMBER'")
    @Column(name = FinanceHubConfig.RETIREMENT_MONTH_MODE_COLUMN, nullable = false, length = 20)
    @Setter
    private RetirementMonthMode retirementMonthMode = RetirementMonthMode.DECEMBER;

    /**
     * Creates a new FinanceHubConfig with the specified name.
     *
     * @param name The unique name for this configuration.
     */
    public FinanceHubConfig(String name) {
        this.name = name;
    }
}
