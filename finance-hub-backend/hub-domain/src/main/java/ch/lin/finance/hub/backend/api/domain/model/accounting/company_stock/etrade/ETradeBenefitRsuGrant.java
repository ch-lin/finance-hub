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
@Table(name = ETradeBenefitRsuGrant.TABLE_NAME, indexes = {
        @Index(name = ETradeBenefitRsuGrant.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseETradeEntity.SYMBOL_COLUMN,
                ETradeBenefitRsuGrant.GRANT_NUMBER_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "grantNumber" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeBenefitRsuGrant extends BaseETradeEntity {

    public static final String TABLE_NAME = "etrade_benefit_rsu_grant";
    public static final String ID_INDEX = "etrade_benefit_rsu_grant_id_index";

    public static final String GRANT_NUMBER_COLUMN = "grant_number";
    public static final String GRANT_DATE_COLUMN = "grant_date";
    public static final String GRANTED_QTY_COLUMN = "granted_qty";
    public static final String VESTED_QTY_COLUMN = "vested_qty";
    public static final String SELLABLE_QTY_COLUMN = "sellable_qty";
    public static final String EST_MARKET_VALUE_COLUMN = "est_market_value";

    @NotNull
    @Column(name = GRANT_NUMBER_COLUMN, nullable = false, length = 50)
    private String grantNumber;

    @Column(name = GRANT_DATE_COLUMN)
    private LocalDate grantDate;

    @ColumnDefault("0")
    @Column(name = GRANTED_QTY_COLUMN)
    private Double grantedQty = 0.0;

    @ColumnDefault("0")
    @Column(name = VESTED_QTY_COLUMN)
    private Double vestedQty = 0.0;

    @ColumnDefault("0")
    @Column(name = SELLABLE_QTY_COLUMN)
    private Double sellableQty = 0.0;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = EST_MARKET_VALUE_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = EST_MARKET_VALUE_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount estMarketValue;

    public ETradeBenefitRsuGrant(String symbol, String grantNumber, LocalDate grantDate, Double grantedQty,
            Double vestedQty,
            Double sellableQty, MonetaryAmount estMarketValue) {
        super(symbol);
        this.grantNumber = grantNumber;
        this.grantDate = grantDate;
        this.grantedQty = grantedQty != null ? grantedQty : 0.0;
        this.vestedQty = vestedQty != null ? vestedQty : 0.0;
        this.sellableQty = sellableQty != null ? sellableQty : 0.0;
        this.estMarketValue = estMarketValue;
    }

    public void merge(ETradeBenefitRsuGrant incoming) {
        if (incoming == null) {
            return;
        }
        this.grantDate = incoming.grantDate;
        this.grantedQty = incoming.grantedQty;
        this.vestedQty = incoming.vestedQty;
        this.sellableQty = incoming.sellableQty;
        this.estMarketValue = incoming.estMarketValue;
    }
}
