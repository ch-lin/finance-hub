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

@Entity
@Table(name = ETradeRsuStockLot.TABLE_NAME, indexes = {
        @Index(name = ETradeRsuStockLot.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseStockLot.SYMBOL_COLUMN,
                ETradeRsuStockLot.GRANT_NUMBER_COLUMN,
                ETradeRsuStockLot.VEST_PERIOD_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "grantNumber", "vestPeriod" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeRsuStockLot extends BaseStockLot {

    public static final String TABLE_NAME = "etrade_rsu_stock_lot";
    public static final String ID_INDEX = "etrade_rsu_stock_lot_id_idx";

    public static final String GRANT_NUMBER_COLUMN = "grant_number";
    public static final String GRANT_DATE_COLUMN = "grant_date";
    public static final String VEST_PERIOD_COLUMN = "vest_period";
    public static final String VEST_DATE_COLUMN = "vest_date";
    public static final String RELEASE_DATE_COLUMN = "release_date";
    public static final String PENDING_SALE_QTY_COLUMN = "pending_sale_qty";

    @NotNull
    @Column(name = GRANT_NUMBER_COLUMN, nullable = false, length = 50)
    private String grantNumber;

    @Column(name = GRANT_DATE_COLUMN)
    private LocalDate grantDate;

    @NotNull
    @Column(name = VEST_PERIOD_COLUMN, nullable = false)
    private Integer vestPeriod;

    @NotNull
    @Column(name = VEST_DATE_COLUMN, nullable = false)
    private LocalDate vestDate;

    @Column(name = RELEASE_DATE_COLUMN)
    private LocalDate releaseDate;

    @ColumnDefault("0")
    @Column(name = PENDING_SALE_QTY_COLUMN)
    private Double pendingSaleQty = 0.0;

    public ETradeRsuStockLot(String symbol, String grantNumber, LocalDate grantDate,
            Integer vestPeriod, LocalDate vestDate, LocalDate releaseDate,
            Double sellableQuantity, Double pendingSaleQty, MonetaryAmount estCostBasis,
            MonetaryAmount estMarketValue, MonetaryAmount expectedGainLoss,
            MonetaryAmount expectedGainLossPerShare, String capitalGainsStatus) {
        super(symbol, sellableQuantity, estCostBasis, estMarketValue, expectedGainLoss,
                expectedGainLossPerShare, capitalGainsStatus);
        this.grantNumber = grantNumber;
        this.grantDate = grantDate;
        this.vestPeriod = vestPeriod;
        this.vestDate = vestDate;
        this.releaseDate = releaseDate;
        this.pendingSaleQty = pendingSaleQty != null ? pendingSaleQty : 0.0;
    }

    public ETradeRsuStockLot(String symbol, String grantNumber, LocalDate grantDate,
            Integer vestPeriod, LocalDate vestDate, Double sellableQuantity,
            MonetaryAmount estCostBasis, MonetaryAmount estMarketValue,
            MonetaryAmount expectedGainLoss, MonetaryAmount expectedGainLossPerShare,
            String capitalGainsStatus) {
        this(symbol, grantNumber, grantDate, vestPeriod, vestDate, null,
                sellableQuantity, 0.0, estCostBasis, estMarketValue, expectedGainLoss,
                expectedGainLossPerShare, capitalGainsStatus);
    }

    public void merge(ETradeRsuStockLot incoming) {
        if (incoming == null) {
            return;
        }
        this.sellableQuantity = incoming.sellableQuantity;
        this.estCostBasis = incoming.estCostBasis;
        this.estMarketValue = incoming.estMarketValue;
        this.expectedGainLoss = incoming.expectedGainLoss;
        this.expectedGainLossPerShare = incoming.expectedGainLossPerShare;
        this.capitalGainsStatus = incoming.capitalGainsStatus;
        this.grantDate = incoming.grantDate;
        this.vestDate = incoming.vestDate;
        this.releaseDate = incoming.releaseDate;
        this.pendingSaleQty = incoming.pendingSaleQty;
    }
}
