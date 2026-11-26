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

import org.hibernate.annotations.ColumnDefault;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = MortgageEarlyRepayment.TABLE_NAME, indexes = {
        @Index(name = MortgageEarlyRepayment.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
})
@Getter
@Setter
@EqualsAndHashCode(of = { "mortgageRecord", "repaymentDate", "amount" }, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MortgageEarlyRepayment extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "mortgage_early_repayment";
    public static final String ID_INDEX = "mortgage_early_repayment_id_index";

    @NotNull
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mortgage_record_id", nullable = false)
    private MortgageRecord mortgageRecord;

    @NotNull
    @Column(name = "repayment_date", nullable = false)
    private LocalDate repaymentDate;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type", nullable = false)
    private EarlyRepaymentType repaymentType;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "fee", nullable = false)
    private Long fee = 0L;

    @Column(name = "reduced_months")
    private Integer reducedMonths;

    public MortgageEarlyRepayment(MortgageRecord mortgageRecord, LocalDate repaymentDate, Long amount,
            EarlyRepaymentType repaymentType, Long fee, Integer reducedMonths) {
        this.mortgageRecord = mortgageRecord;
        this.repaymentDate = repaymentDate;
        this.amount = amount;
        this.repaymentType = repaymentType;
        this.fee = fee != null ? fee : 0L;
        this.reducedMonths = reducedMonths;
    }
}
