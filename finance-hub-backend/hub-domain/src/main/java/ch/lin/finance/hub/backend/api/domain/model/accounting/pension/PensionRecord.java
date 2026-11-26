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
package ch.lin.finance.hub.backend.api.domain.model.accounting.pension;

import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a Japanese Pension Record (Nenkin Teikibin or Hihokensha Kiroku
 * Shoukai Kaitouhyou). Used for tracking accrued pension assets and projecting
 * future retirement cash flows.
 */
@Entity
@Table(name = PensionRecord.TABLE_NAME, indexes = {
        @Index(name = PensionRecord.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                PensionRecord.RECORD_DATE_COLUMN })
})
@Getter
@EqualsAndHashCode(of = "recordDate", callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PensionRecord extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "pension_record";
    public static final String ID_INDEX = "pension_record_id_index";
    public static final String RECORD_DATE_COLUMN = "record_date";
    public static final String BASIC_PENSION_AMOUNT_COLUMN = "basic_pension_amount";
    public static final String KOUSEI_PENSION_AMOUNT_COLUMN = "kousei_pension_amount";
    public static final String TOTAL_PENSION_AMOUNT_COLUMN = "total_pension_amount";
    public static final String KOKUMIN_CATEGORY1_MONTHS_COLUMN = "kokumin_category1_months";
    public static final String KOKUMIN_CATEGORY3_MONTHS_COLUMN = "kokumin_category3_months";
    public static final String KOKUMIN_TOTAL_MONTHS_COLUMN = "kokumin_total_months";
    public static final String KOUSEI_GENERAL_MONTHS_COLUMN = "kousei_general_months";
    public static final String KOUSEI_TOTAL_MONTHS_COLUMN = "kousei_total_months";
    public static final String TOTAL_ENROLLED_MONTHS_COLUMN = "total_enrolled_months";
    public static final String QUALIFYING_MONTHS_COLUMN = "qualifying_months";
    public static final String KOKUMIN_PREMIUM_PAID_COLUMN = "kokumin_premium_paid";
    public static final String KOUSEI_PREMIUM_PAID_COLUMN = "kousei_premium_paid";
    public static final String TOTAL_PREMIUM_PAID_COLUMN = "total_premium_paid";

    /**
     * The date this record represents (e.g., the date on the Nenkin Teikibin).
     */
    @NotNull
    @Column(name = PensionRecord.RECORD_DATE_COLUMN, nullable = false)
    private LocalDate recordDate;

    // --- Estimated Annual Pension Amounts ---
    /**
     * Estimated annual basic pension amount (老齢基礎年金).
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.BASIC_PENSION_AMOUNT_COLUMN)
    private Long basicPensionAmount = 0L;

    /**
     * Estimated annual employees' pension amount (老齢厚生年金).
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOUSEI_PENSION_AMOUNT_COLUMN)
    private Long kouseiPensionAmount = 0L;

    /**
     * Estimated total annual pension amount.
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.TOTAL_PENSION_AMOUNT_COLUMN)
    private Long totalPensionAmount = 0L;

    // --- Enrolled Months ---
    /**
     * Enrolled months for National Pension Category 1 (国民年金 第1号).
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOKUMIN_CATEGORY1_MONTHS_COLUMN)
    private Integer kokuminCategory1Months = 0;

    /**
     * Enrolled months for National Pension Category 3 (国民年金 第3号).
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOKUMIN_CATEGORY3_MONTHS_COLUMN)
    private Integer kokuminCategory3Months = 0;

    /**
     * Total enrolled months in the National Pension system.
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOKUMIN_TOTAL_MONTHS_COLUMN)
    private Integer kokuminTotalMonths = 0;

    /**
     * Enrolled months for general Employees' Pension (厚生年金 一般).
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOUSEI_GENERAL_MONTHS_COLUMN)
    private Integer kouseiGeneralMonths = 0;

    /**
     * Total enrolled months in the Employees' Pension system.
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOUSEI_TOTAL_MONTHS_COLUMN)
    private Integer kouseiTotalMonths = 0;

    /**
     * Total enrolled months across all pension systems.
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.TOTAL_ENROLLED_MONTHS_COLUMN)
    private Integer totalEnrolledMonths = 0;

    /**
     * Total qualifying months for receiving a pension (受給資格期間).
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.QUALIFYING_MONTHS_COLUMN)
    private Integer qualifyingMonths = 0;

    // --- Premiums Paid ---
    /**
     * Cumulative premiums paid to the National Pension.
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOKUMIN_PREMIUM_PAID_COLUMN)
    private Long kokuminPremiumPaid = 0L;

    /**
     * Cumulative premiums paid to the Employees' Pension.
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.KOUSEI_PREMIUM_PAID_COLUMN)
    private Long kouseiPremiumPaid = 0L;

    /**
     * Cumulative total premiums paid.
     */
    @ColumnDefault("0")
    @Column(name = PensionRecord.TOTAL_PREMIUM_PAID_COLUMN)
    private Long totalPremiumPaid = 0L;

    /**
     * Creates a new PensionRecord with all fields initialized.
     *
     * @param recordDate             The date of the record.
     * @param basicPensionAmount     Basic pension amount.
     * @param kouseiPensionAmount    Employees' pension amount.
     * @param totalPensionAmount     Total pension amount.
     * @param kokuminCategory1Months National pension category 1 months.
     * @param kokuminCategory3Months National pension category 3 months.
     * @param kokuminTotalMonths     Total national pension months.
     * @param kouseiGeneralMonths    General employees' pension months.
     * @param kouseiTotalMonths      Total employees' pension months.
     * @param totalEnrolledMonths    Total enrolled months.
     * @param qualifyingMonths       Qualifying months.
     * @param kokuminPremiumPaid     Premiums paid for national pension.
     * @param kouseiPremiumPaid      Premiums paid for employees' pension.
     * @param totalPremiumPaid       Total premiums paid.
     */
    public PensionRecord(LocalDate recordDate, Long basicPensionAmount, Long kouseiPensionAmount,
            Long totalPensionAmount, Integer kokuminCategory1Months, Integer kokuminCategory3Months,
            Integer kokuminTotalMonths, Integer kouseiGeneralMonths, Integer kouseiTotalMonths,
            Integer totalEnrolledMonths, Integer qualifyingMonths, Long kokuminPremiumPaid,
            Long kouseiPremiumPaid, Long totalPremiumPaid) {
        this.recordDate = recordDate;
        this.basicPensionAmount = basicPensionAmount != null ? basicPensionAmount : 0L;
        this.kouseiPensionAmount = kouseiPensionAmount != null ? kouseiPensionAmount : 0L;
        this.totalPensionAmount = totalPensionAmount != null ? totalPensionAmount : 0L;
        this.kokuminCategory1Months = kokuminCategory1Months != null ? kokuminCategory1Months : 0;
        this.kokuminCategory3Months = kokuminCategory3Months != null ? kokuminCategory3Months : 0;
        this.kokuminTotalMonths = kokuminTotalMonths != null ? kokuminTotalMonths : 0;
        this.kouseiGeneralMonths = kouseiGeneralMonths != null ? kouseiGeneralMonths : 0;
        this.kouseiTotalMonths = kouseiTotalMonths != null ? kouseiTotalMonths : 0;
        this.totalEnrolledMonths = totalEnrolledMonths != null ? totalEnrolledMonths : 0;
        this.qualifyingMonths = qualifyingMonths != null ? qualifyingMonths : 0;
        this.kokuminPremiumPaid = kokuminPremiumPaid != null ? kokuminPremiumPaid : 0L;
        this.kouseiPremiumPaid = kouseiPremiumPaid != null ? kouseiPremiumPaid : 0L;
        this.totalPremiumPaid = totalPremiumPaid != null ? totalPremiumPaid : 0L;
    }

    public void merge(PensionRecord incoming) {
        if (incoming == null) {
            return;
        }
        this.basicPensionAmount = incoming.basicPensionAmount;
        this.kouseiPensionAmount = incoming.kouseiPensionAmount;
        this.totalPensionAmount = incoming.totalPensionAmount;
        this.kokuminCategory1Months = incoming.kokuminCategory1Months;
        this.kokuminCategory3Months = incoming.kokuminCategory3Months;
        this.kokuminTotalMonths = incoming.kokuminTotalMonths;
        this.kouseiGeneralMonths = incoming.kouseiGeneralMonths;
        this.kouseiTotalMonths = incoming.kouseiTotalMonths;
        this.totalEnrolledMonths = incoming.totalEnrolledMonths;
        this.qualifyingMonths = incoming.qualifyingMonths;
        this.kokuminPremiumPaid = incoming.kokuminPremiumPaid;
        this.kouseiPremiumPaid = incoming.kouseiPremiumPaid;
        this.totalPremiumPaid = incoming.totalPremiumPaid;
    }
}
