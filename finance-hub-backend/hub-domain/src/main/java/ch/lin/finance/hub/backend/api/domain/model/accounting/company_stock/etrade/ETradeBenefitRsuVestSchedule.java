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
package ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade;

import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = ETradeBenefitRsuVestSchedule.TABLE_NAME, indexes = {
        @Index(name = ETradeBenefitRsuVestSchedule.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseETradeEntity.SYMBOL_COLUMN,
                ETradeBenefitRsuVestSchedule.GRANT_NUMBER_COLUMN,
                ETradeBenefitRsuVestSchedule.VEST_PERIOD_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "grantNumber", "vestPeriod" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeBenefitRsuVestSchedule extends BaseETradeEntity {

    public static final String TABLE_NAME = "etrade_benefit_rsu_vest_schedule";
    public static final String ID_INDEX = "etrade_benefit_rsu_vest_sched_id_idx";

    public static final String GRANT_NUMBER_COLUMN = "grant_number";
    public static final String VEST_PERIOD_COLUMN = "vest_period";
    public static final String VEST_DATE_COLUMN = "vest_date";
    public static final String GRANTED_QTY_COLUMN = "granted_qty";
    public static final String VESTED_QTY_COLUMN = "vested_qty";
    public static final String RELEASED_QTY_COLUMN = "released_qty";
    public static final String SELLABLE_QTY_COLUMN = "sellable_qty";
    public static final String TAXABLE_GAIN_COLUMN = "taxable_gain";
    public static final String TAX_WITHHELD_COLUMN = "tax_withheld";
    public static final String TOTAL_TAXES_PAID_COLUMN = "total_taxes_paid";
    public static final String TAX_STATUS_COLUMN = "tax_status";

    @NotNull
    @Column(name = GRANT_NUMBER_COLUMN, nullable = false, length = 50)
    private String grantNumber;

    @NotNull
    @Column(name = VEST_PERIOD_COLUMN, nullable = false)
    private Integer vestPeriod;

    @Column(name = VEST_DATE_COLUMN)
    private LocalDate vestDate;

    @ColumnDefault("0")
    @Column(name = GRANTED_QTY_COLUMN)
    private Double grantedQty = 0.0;

    @ColumnDefault("0")
    @Column(name = VESTED_QTY_COLUMN)
    private Double vestedQty = 0.0;

    @ColumnDefault("0")
    @Column(name = RELEASED_QTY_COLUMN)
    private Double releasedQty = 0.0;

    @ColumnDefault("0")
    @Column(name = SELLABLE_QTY_COLUMN)
    private Double sellableQty = 0.0;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = TAXABLE_GAIN_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = TAXABLE_GAIN_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount taxableGain;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = TAX_WITHHELD_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = TAX_WITHHELD_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount taxWithheld;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = TOTAL_TAXES_PAID_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = TOTAL_TAXES_PAID_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount totalTaxesPaid;

    @Column(name = TAX_STATUS_COLUMN, length = 50)
    private String taxStatus;

    public ETradeBenefitRsuVestSchedule(String symbol, String grantNumber, Integer vestPeriod, LocalDate vestDate,
            Double grantedQty, Double vestedQty, Double releasedQty, Double sellableQty,
            MonetaryAmount taxableGain, MonetaryAmount taxWithheld, MonetaryAmount totalTaxesPaid, String taxStatus) {
        super(symbol);
        this.grantNumber = grantNumber;
        this.vestPeriod = vestPeriod;
        this.vestDate = vestDate;
        this.grantedQty = grantedQty != null ? grantedQty : 0.0;
        this.vestedQty = vestedQty != null ? vestedQty : 0.0;
        this.releasedQty = releasedQty != null ? releasedQty : 0.0;
        this.sellableQty = sellableQty != null ? sellableQty : 0.0;
        this.taxableGain = taxableGain;
        this.taxWithheld = taxWithheld;
        this.totalTaxesPaid = totalTaxesPaid;
        this.taxStatus = taxStatus;
    }

    public void merge(ETradeBenefitRsuVestSchedule incoming) {
        if (incoming == null) {
            return;
        }
        this.vestDate = incoming.vestDate;
        this.grantedQty = incoming.grantedQty;
        this.vestedQty = incoming.vestedQty;
        this.releasedQty = incoming.releasedQty;
        this.sellableQty = incoming.sellableQty;
        this.taxableGain = incoming.taxableGain;
        this.taxWithheld = incoming.taxWithheld;
        this.totalTaxesPaid = incoming.totalTaxesPaid;
        this.taxStatus = incoming.taxStatus;
    }
}
