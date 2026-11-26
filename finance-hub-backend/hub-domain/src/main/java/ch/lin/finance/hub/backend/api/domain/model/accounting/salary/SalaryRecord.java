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
package ch.lin.finance.hub.backend.api.domain.model.accounting.salary;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a monthly salary statement (給与明細書) from the payroll system. Stores
 * both core explicit fields (income, tax, pension, espp) for reporting, and a
 * dynamic JSON details column to capture variable line items without losing
 * data.
 */
@Entity
@Table(name = SalaryRecord.TABLE_NAME, indexes = {
    @Index(name = SalaryRecord.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
        SalaryRecord.PAYMENT_DATE_COLUMN})
})
@Getter
@lombok.EqualsAndHashCode(of = "paymentDate", callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalaryRecord extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "salary_record";
    public static final String ID_INDEX = "salary_record_id_index";
    public static final String PAYMENT_DATE_COLUMN = "payment_date";
    public static final String TYPE_COLUMN = "type";
    public static final String GROSS_AMOUNT_COLUMN = "gross_amount";
    public static final String NET_AMOUNT_COLUMN = "net_amount";
    public static final String BASE_SALARY_COLUMN = "base_salary";
    public static final String OVERTIME_PAY_COLUMN = "overtime_pay";
    public static final String COMMISSION_COLUMN = "commission";
    public static final String FITNESS_ALLOWANCE_COLUMN = "fitness_allowance";
    public static final String HEALTH_INSURANCE_COLUMN = "health_insurance";
    public static final String PENSION_COLUMN = "pension";
    public static final String EMPLOYMENT_INSURANCE_COLUMN = "employment_insurance";
    public static final String INCOME_TAX_COLUMN = "income_tax";
    public static final String RESIDENT_TAX_COLUMN = "resident_tax";
    public static final String ESPP_CONTRIBUTION_COLUMN = "espp_contribution";
    public static final String NOTE_COLUMN = "note";
    public static final String DETAILS_COLUMN = "details";

    /**
     * The payment date of the salary.
     */
    @NotNull
    @Column(name = SalaryRecord.PAYMENT_DATE_COLUMN, nullable = false)
    private LocalDate paymentDate;

    /**
     * The type of the salary record (e.g., MONTHLY_SALARY, BONUS).
     */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = SalaryRecord.TYPE_COLUMN, nullable = false)
    private SalaryType type = SalaryType.MONTHLY_SALARY;

    /**
     * The total gross amount of the salary before any deductions.
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.GROSS_AMOUNT_COLUMN)
    private Long grossAmount = 0L;

    /**
     * The net amount of the salary deposited into the bank account.
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.NET_AMOUNT_COLUMN)
    private Long netAmount = 0L;

    /**
     * The base salary amount.
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.BASE_SALARY_COLUMN)
    private Long baseSalary = 0L;

    /**
     * The amount of overtime pay.
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.OVERTIME_PAY_COLUMN)
    private Long overtimePay = 0L;

    /**
     * The amount of commission or variable bonus.
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.COMMISSION_COLUMN)
    private Long commission = 0L;

    /**
     * The amount for fitness allowance.
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.FITNESS_ALLOWANCE_COLUMN)
    private Long fitnessAllowance = 0L;

    /**
     * The deducted amount for health insurance (健康保険料).
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.HEALTH_INSURANCE_COLUMN)
    private Long healthInsurance = 0L;

    /**
     * The deducted amount for employee pension insurance (厚生年金保険料).
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.PENSION_COLUMN)
    private Long pension = 0L;

    /**
     * The deducted amount for employment insurance (雇用保険料).
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.EMPLOYMENT_INSURANCE_COLUMN)
    private Long employmentInsurance = 0L;

    /**
     * The deducted amount for income tax (所得税).
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.INCOME_TAX_COLUMN)
    private Long incomeTax = 0L;

    /**
     * The deducted amount for resident tax (住民税).
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.RESIDENT_TAX_COLUMN)
    private Long residentTax = 0L;

    /**
     * The deducted amount for the Employee Stock Purchase Plan (ESPP/持株会).
     */
    @Setter
    @ColumnDefault("0")
    @Column(name = SalaryRecord.ESPP_CONTRIBUTION_COLUMN)
    private Long esppContribution = 0L;

    /**
     * Any additional notes regarding the salary.
     */
    @Setter
    @Column(name = SalaryRecord.NOTE_COLUMN, columnDefinition = "TEXT")
    private String note;

    /**
     * Dynamic JSON map for unmapped or variable salary line items.
     */
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = SalaryRecord.DETAILS_COLUMN, columnDefinition = "json")
    private Map<String, Long> details = new HashMap<>();

    /**
     * Creates a new SalaryRecord for the specified payment date.
     *
     * @param paymentDate the date the salary is paid
     */
    public SalaryRecord(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    /**
     * Validates and sanitizes the state of the entity based on its type.
     * Enforces domain invariants for BONUS and MONTHLY_SALARY.
     */
    public void validateAndSanitize() {
        if (SalaryType.BONUS.equals(this.type)) {
            // For BONUS, these fields are strictly zero.
            this.baseSalary = 0L;
            this.overtimePay = 0L;
            this.fitnessAllowance = 0L;
            this.residentTax = 0L;
            this.esppContribution = 0L;
        }
    }
}
