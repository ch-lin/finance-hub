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
package ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a mortgage or long-term liability record.
 */
@Entity
@Table(name = MortgageRecord.TABLE_NAME, indexes = {
        @Index(name = MortgageRecord.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
})
@Getter
@Setter
@EqualsAndHashCode(of = { "bankName", "contractNumber" }, callSuper = false)
public class MortgageRecord extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "mortgage_record";
    public static final String ID_INDEX = "mortgage_record_id_index";

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "contract_number")
    private String contractNumber;

    @NotNull
    @Column(name = "loan_name", nullable = false)
    private String loanName;

    @NotNull
    @Column(name = "total_loan_amount", nullable = false)
    private Long totalLoanAmount;

    @NotNull
    @Column(name = "current_balance", nullable = false)
    private Long currentBalance;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Column(name = "balance_record_date", nullable = false)
    private LocalDate balanceRecordDate;

    @NotNull
    @Column(name = "loan_term_months", nullable = false)
    private Integer loanTermMonths;

    @NotNull
    @Column(name = "interest_rate", nullable = false)
    private Double interestRate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false)
    private InterestRateType rateType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_method", nullable = false)
    private RepaymentMethod repaymentMethod;

    @NotNull
    @Column(name = "monthly_payment", nullable = false)
    private Long monthlyPayment;

    @ElementCollection
    @CollectionTable(name = "mortgage_bonus_payment", joinColumns = @JoinColumn(name = "mortgage_record_id"))
    private List<BonusPayment> bonusPayments;

    @Column(name = "occupancy_date")
    private LocalDate occupancyDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "residence_type")
    private ResidenceType residenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type")
    private PropertyType propertyType;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "tax_deduction_eligible", nullable = false)
    private Boolean taxDeductionEligible = false;

    @Column(name = "tax_deduction_end_year")
    private Integer taxDeductionEndYear;

    @Column(name = "tax_deduction_rate")
    private Double taxDeductionRate;

    @Column(name = "max_tax_deduction_amount")
    private Long maxTaxDeductionAmount;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "has_five_year_rule", nullable = false)
    private Boolean hasFiveYearRule = true;

    @jakarta.persistence.OneToMany(mappedBy = "mortgageRecord", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private java.util.List<MortgageEarlyRepayment> earlyRepayments = new java.util.ArrayList<>();

    public MortgageRecord(String bankName, String contractNumber, String loanName, Long totalLoanAmount, Long currentBalance, LocalDate startDate,
            LocalDate endDate, LocalDate balanceRecordDate, Integer loanTermMonths, Double interestRate,
            InterestRateType rateType, RepaymentMethod repaymentMethod, Long monthlyPayment,
            List<BonusPayment> bonusPayments, LocalDate occupancyDate, ResidenceType residenceType,
            PropertyType propertyType, Boolean taxDeductionEligible, Integer taxDeductionEndYear,
            Double taxDeductionRate, Long maxTaxDeductionAmount, Boolean hasFiveYearRule) {
        this.bonusPayments = new ArrayList<>();
        this.bankName = bankName;
        this.contractNumber = contractNumber;
        this.loanName = loanName;
        this.totalLoanAmount = totalLoanAmount;
        this.currentBalance = currentBalance;
        this.startDate = startDate;
        this.endDate = endDate;
        this.balanceRecordDate = balanceRecordDate != null ? balanceRecordDate : LocalDate.now();
        this.loanTermMonths = loanTermMonths;
        this.interestRate = interestRate;
        this.rateType = rateType;
        this.repaymentMethod = repaymentMethod;
        this.monthlyPayment = monthlyPayment;
        this.bonusPayments = bonusPayments != null ? bonusPayments : new ArrayList<>();
        this.occupancyDate = occupancyDate;
        this.residenceType = residenceType;
        this.propertyType = propertyType;
        this.taxDeductionEligible = taxDeductionEligible != null ? taxDeductionEligible : false;
        this.taxDeductionEndYear = taxDeductionEndYear;
        this.taxDeductionRate = taxDeductionRate;
        this.maxTaxDeductionAmount = maxTaxDeductionAmount;
        this.hasFiveYearRule = hasFiveYearRule != null ? hasFiveYearRule : true;
    }

    /**
     * Convenience constructor for facts parsed from bank statements.
     * User-managed fields default to null / default settings.
     */
    public MortgageRecord(String bankName, String contractNumber, String loanName, Long totalLoanAmount,
            Long currentBalance, LocalDate startDate, LocalDate endDate, LocalDate balanceRecordDate,
            Integer loanTermMonths, Double interestRate, InterestRateType rateType,
            RepaymentMethod repaymentMethod, Long monthlyPayment, List<BonusPayment> bonusPayments) {
        this(bankName, contractNumber, loanName, totalLoanAmount, currentBalance, startDate, endDate,
                balanceRecordDate, loanTermMonths, interestRate, rateType, repaymentMethod, monthlyPayment,
                bonusPayments, null, null, null, false, null, null, null, true);
    }

    /**
     * Merges incoming mortgage record facts into this record:
     * - Overwrites all 14 bank fact fields unconditionally.
     * - Leaves user-managed fields (occupancyDate, residenceType, propertyType,
     *   tax deductions, hasFiveYearRule, earlyRepayments) strictly untouched.
     *
     * @param incoming The parsed mortgage record with updated facts.
     */
    public void merge(MortgageRecord incoming) {
        if (incoming == null) {
            return;
        }
        this.bankName = incoming.getBankName();
        this.contractNumber = incoming.getContractNumber();
        this.loanName = incoming.getLoanName();
        this.totalLoanAmount = incoming.getTotalLoanAmount();
        this.currentBalance = incoming.getCurrentBalance();
        this.startDate = incoming.getStartDate();
        this.endDate = incoming.getEndDate();
        this.balanceRecordDate = incoming.getBalanceRecordDate();
        this.loanTermMonths = incoming.getLoanTermMonths();
        this.interestRate = incoming.getInterestRate();
        this.rateType = incoming.getRateType();
        this.repaymentMethod = incoming.getRepaymentMethod();
        this.monthlyPayment = incoming.getMonthlyPayment();
        if (this.bonusPayments == null) {
            this.bonusPayments = new ArrayList<>();
        }
        this.bonusPayments.clear();
        if (incoming.getBonusPayments() != null) {
            this.bonusPayments.addAll(incoming.getBonusPayments());
        }
    }

    @java.lang.SuppressWarnings(value = "all")
    @lombok.Generated
    protected MortgageRecord() {
        this.bonusPayments = new ArrayList<>();
    }
}
