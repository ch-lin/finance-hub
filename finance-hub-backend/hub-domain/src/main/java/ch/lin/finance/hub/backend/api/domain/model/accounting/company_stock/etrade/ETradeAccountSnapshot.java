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
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
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
@Table(name = ETradeAccountSnapshot.TABLE_NAME, indexes = {
        @Index(name = ETradeAccountSnapshot.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseETradeEntity.SYMBOL_COLUMN,
                ETradeAccountSnapshot.RECORD_DATE_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "recordDate" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeAccountSnapshot extends BaseETradeEntity {

    public static final String TABLE_NAME = "etrade_account_snapshot";
    public static final String ID_INDEX = "etrade_account_snapshot_id_index";
    public static final String RECORD_DATE_COLUMN = "record_date";

    public static final String TOTAL_ACCOUNT_VALUE_COLUMN = "total_account_value";
    public static final String CURRENT_ACCOUNT_VALUE_COLUMN = "current_account_value";
    public static final String POTENTIAL_BENEFIT_VALUE_COLUMN = "potential_benefit_value";
    public static final String CURRENCY_COLUMN = "currency";

    public static final String ESPP_PURCHASED_QTY_COLUMN = "espp_purchased_qty";
    public static final String RS_GRANTED_QTY_COLUMN = "rs_granted_qty";
    public static final String EXCHANGE_RATE_COLUMN = "exchange_rate";

    @NotNull
    @Column(name = RECORD_DATE_COLUMN, nullable = false)
    private LocalDate recordDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = TOTAL_ACCOUNT_VALUE_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = TOTAL_ACCOUNT_VALUE_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount totalAccountValue;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = CURRENT_ACCOUNT_VALUE_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = CURRENT_ACCOUNT_VALUE_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount currentAccountValue;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = POTENTIAL_BENEFIT_VALUE_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = POTENTIAL_BENEFIT_VALUE_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount potentialBenefitValue;

    @ColumnDefault("0")
    @Column(name = ESPP_PURCHASED_QTY_COLUMN)
    private Double esppPurchasedQty = 0.0;

    @ColumnDefault("0")
    @Column(name = RS_GRANTED_QTY_COLUMN)
    private Double rsGrantedQty = 0.0;

    @Column(name = EXCHANGE_RATE_COLUMN)
    private Double exchangeRate;

    public ETradeAccountSnapshot(String symbol, LocalDate recordDate, MonetaryAmount totalAccountValue,
            MonetaryAmount currentAccountValue, MonetaryAmount potentialBenefitValue,
            Double esppPurchasedQty, Double rsGrantedQty, Double exchangeRate) {
        super(symbol);
        this.recordDate = recordDate;
        this.totalAccountValue = totalAccountValue;
        this.currentAccountValue = currentAccountValue;
        this.potentialBenefitValue = potentialBenefitValue;
        this.esppPurchasedQty = esppPurchasedQty != null ? esppPurchasedQty : 0.0;
        this.rsGrantedQty = rsGrantedQty != null ? rsGrantedQty : 0.0;
        this.exchangeRate = exchangeRate;
    }

    public ETradeAccountSnapshot(String symbol, LocalDate recordDate, Long totalAccountValue,
            Long currentAccountValue, Long potentialBenefitValue, CurrencyCode currency) {
        this(symbol, recordDate, MonetaryAmount.of(totalAccountValue, currency),
                MonetaryAmount.of(currentAccountValue, currency), MonetaryAmount.of(potentialBenefitValue, currency),
                0.0, 0.0, null);
    }

    public void merge(ETradeAccountSnapshot incoming) {
        if (incoming == null) {
            return;
        }
        this.totalAccountValue = incoming.totalAccountValue;
        this.currentAccountValue = incoming.currentAccountValue;
        this.potentialBenefitValue = incoming.potentialBenefitValue;
        this.esppPurchasedQty = incoming.esppPurchasedQty;
        this.rsGrantedQty = incoming.rsGrantedQty;
        this.exchangeRate = incoming.exchangeRate;
    }
}
