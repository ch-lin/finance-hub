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
@Table(name = ETradeEsppStockLot.TABLE_NAME, indexes = {
        @Index(name = ETradeEsppStockLot.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseStockLot.SYMBOL_COLUMN,
                ETradeEsppStockLot.PURCHASE_DATE_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "purchaseDate" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeEsppStockLot extends BaseStockLot {

    public static final String TABLE_NAME = "etrade_espp_stock_lot";
    public static final String ID_INDEX = "etrade_espp_stock_lot_id_idx";

    public static final String PURCHASE_DATE_COLUMN = "purchase_date";
    public static final String PURCHASE_PRICE_COLUMN = "purchase_price";
    public static final String PURCHASED_QTY_COLUMN = "purchased_qty";
    public static final String PURCHASE_DATE_FMV_COLUMN = "purchase_date_fmv";
    public static final String GRANT_DATE_FMV_COLUMN = "grant_date_fmv";
    public static final String DISCOUNT_PERCENT_COLUMN = "discount_percent";
    public static final String GRANT_DATE_COLUMN = "grant_date";
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

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = PURCHASE_DATE_FMV_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = PURCHASE_DATE_FMV_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount purchaseDateFmv;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = GRANT_DATE_FMV_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = GRANT_DATE_FMV_COLUMN
                    + "_currency", length = 10))
    })
    private MonetaryAmount grantDateFmv;

    @Column(name = DISCOUNT_PERCENT_COLUMN)
    private Double discountPercent;

    @Column(name = GRANT_DATE_COLUMN)
    private LocalDate grantDate;

    @Column(name = QUALIFIED_PLAN_COLUMN, length = 20)
    private String qualifiedPlan;

    @Column(name = TRANSFERABLE_DATE_COLUMN)
    private LocalDate transferableDate;

    public ETradeEsppStockLot(String symbol, LocalDate purchaseDate, MonetaryAmount purchasePrice,
            Double purchasedQty, Double sellableQuantity, MonetaryAmount estCostBasis,
            MonetaryAmount purchaseDateFmv, MonetaryAmount grantDateFmv, Double discountPercent,
            LocalDate grantDate, MonetaryAmount estMarketValue, MonetaryAmount expectedGainLoss,
            MonetaryAmount expectedGainLossPerShare, String capitalGainsStatus,
            String qualifiedPlan, LocalDate transferableDate) {
        super(symbol, sellableQuantity, estCostBasis, estMarketValue, expectedGainLoss,
                expectedGainLossPerShare, capitalGainsStatus);
        this.purchaseDate = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.purchasedQty = purchasedQty != null ? purchasedQty : 0.0;
        this.purchaseDateFmv = purchaseDateFmv;
        this.grantDateFmv = grantDateFmv;
        this.discountPercent = discountPercent;
        this.grantDate = grantDate;
        this.qualifiedPlan = qualifiedPlan;
        this.transferableDate = transferableDate;
    }

    public ETradeEsppStockLot(String symbol, LocalDate purchaseDate, MonetaryAmount purchasePrice,
            Double purchasedQty, Double sellableQuantity, MonetaryAmount estCostBasis,
            MonetaryAmount estMarketValue, MonetaryAmount expectedGainLoss, Double discountPercent) {
        this(symbol, purchaseDate, purchasePrice, purchasedQty, sellableQuantity, estCostBasis,
                null, null, discountPercent, null, estMarketValue, expectedGainLoss, null, null, null, null);
    }

    public void merge(ETradeEsppStockLot incoming) {
        if (incoming == null) {
            return;
        }
        this.sellableQuantity = incoming.sellableQuantity;
        this.estCostBasis = incoming.estCostBasis;
        this.estMarketValue = incoming.estMarketValue;
        this.expectedGainLoss = incoming.expectedGainLoss;
        this.expectedGainLossPerShare = incoming.expectedGainLossPerShare;
        this.capitalGainsStatus = incoming.capitalGainsStatus;
        this.purchasePrice = incoming.purchasePrice;
        this.purchasedQty = incoming.purchasedQty;
        this.purchaseDateFmv = incoming.purchaseDateFmv;
        this.grantDateFmv = incoming.grantDateFmv;
        this.discountPercent = incoming.discountPercent;
        this.grantDate = incoming.grantDate;
        this.qualifiedPlan = incoming.qualifiedPlan;
        this.transferableDate = incoming.transferableDate;
    }
}
