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
@Table(name = ETradeBenefitEsppPurchase.TABLE_NAME, indexes = {
        @Index(name = ETradeBenefitEsppPurchase.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseETradeEntity.SYMBOL_COLUMN,
                ETradeBenefitEsppPurchase.PURCHASE_DATE_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "purchaseDate" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeBenefitEsppPurchase extends BaseETradeEntity {

    public static final String TABLE_NAME = "etrade_benefit_espp_purchase";
    public static final String ID_INDEX = "etrade_benefit_espp_purchase_id_idx";

    public static final String PURCHASE_DATE_COLUMN = "purchase_date";
    public static final String PURCHASE_PRICE_COLUMN = "purchase_price";
    public static final String PURCHASED_QTY_COLUMN = "purchased_qty";
    public static final String SELLABLE_QTY_COLUMN = "sellable_qty";
    public static final String EST_MARKET_VALUE_COLUMN = "est_market_value";
    public static final String GRANT_DATE_COLUMN = "grant_date";
    public static final String DISCOUNT_PERCENT_COLUMN = "discount_percent";
    public static final String GRANT_DATE_FMV_COLUMN = "grant_date_fmv";
    public static final String PURCHASE_DATE_FMV_COLUMN = "purchase_date_fmv";
    public static final String QUALIFIED_PLAN_COLUMN = "qualified_plan";
    public static final String TRANSFERABLE_DATE_COLUMN = "transferable_date";

    @NotNull
    @Column(name = PURCHASE_DATE_COLUMN, nullable = false)
    private LocalDate purchaseDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = PURCHASE_PRICE_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = PURCHASE_PRICE_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount purchasePrice;

    @ColumnDefault("0")
    @Column(name = PURCHASED_QTY_COLUMN)
    private Double purchasedQty = 0.0;

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

    @Column(name = GRANT_DATE_COLUMN)
    private LocalDate grantDate;

    @ColumnDefault("0")
    @Column(name = DISCOUNT_PERCENT_COLUMN)
    private Double discountPercent = 0.0;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = GRANT_DATE_FMV_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = GRANT_DATE_FMV_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount grantDateFmv;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = PURCHASE_DATE_FMV_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = PURCHASE_DATE_FMV_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount purchaseDateFmv;

    @Column(name = QUALIFIED_PLAN_COLUMN)
    private Boolean qualifiedPlan;

    @Column(name = TRANSFERABLE_DATE_COLUMN)
    private LocalDate transferableDate;

    public ETradeBenefitEsppPurchase(String symbol, LocalDate purchaseDate, MonetaryAmount purchasePrice,
            Double purchasedQty,
            Double sellableQty, MonetaryAmount estMarketValue, LocalDate grantDate, Double discountPercent,
            MonetaryAmount grantDateFmv, MonetaryAmount purchaseDateFmv, Boolean qualifiedPlan,
            LocalDate transferableDate) {
        super(symbol);
        this.purchaseDate = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.purchasedQty = purchasedQty != null ? purchasedQty : 0.0;
        this.sellableQty = sellableQty != null ? sellableQty : 0.0;
        this.estMarketValue = estMarketValue;
        this.grantDate = grantDate;
        this.discountPercent = discountPercent != null ? discountPercent : 0.0;
        this.grantDateFmv = grantDateFmv;
        this.purchaseDateFmv = purchaseDateFmv;
        this.qualifiedPlan = qualifiedPlan;
        this.transferableDate = transferableDate;
    }

    public void merge(ETradeBenefitEsppPurchase incoming) {
        if (incoming == null) {
            return;
        }
        this.purchasePrice = incoming.purchasePrice;
        this.purchasedQty = incoming.purchasedQty;
        this.sellableQty = incoming.sellableQty;
        this.estMarketValue = incoming.estMarketValue;
        this.grantDate = incoming.grantDate;
        this.discountPercent = incoming.discountPercent;
        this.grantDateFmv = incoming.grantDateFmv;
        this.purchaseDateFmv = incoming.purchaseDateFmv;
        this.qualifiedPlan = incoming.qualifiedPlan;
        this.transferableDate = incoming.transferableDate;
    }
}
