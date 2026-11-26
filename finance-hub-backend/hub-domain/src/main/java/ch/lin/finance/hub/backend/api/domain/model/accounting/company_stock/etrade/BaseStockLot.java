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

import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@MappedSuperclass
@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseStockLot extends BaseETradeEntity {

    public static final String SELLABLE_QUANTITY_COLUMN = "sellable_quantity";
    public static final String EST_COST_BASIS_COLUMN = "est_cost_basis";
    public static final String EST_MARKET_VALUE_COLUMN = "est_market_value";
    public static final String EXPECTED_GAIN_LOSS_COLUMN = "expected_gain_loss";
    public static final String EXPECTED_GAIN_LOSS_PER_SHARE_COLUMN = "expected_gain_loss_per_share";
    public static final String CAPITAL_GAINS_STATUS_COLUMN = "capital_gains_status";

    @Column(name = SELLABLE_QUANTITY_COLUMN)
    protected Double sellableQuantity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = EST_COST_BASIS_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = EST_COST_BASIS_COLUMN
                    + "_currency", length = 10))
    })
    protected MonetaryAmount estCostBasis;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = EST_MARKET_VALUE_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = EST_MARKET_VALUE_COLUMN
                    + "_currency", length = 10))
    })
    protected MonetaryAmount estMarketValue;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = EXPECTED_GAIN_LOSS_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = EXPECTED_GAIN_LOSS_COLUMN
                    + "_currency", length = 10))
    })
    protected MonetaryAmount expectedGainLoss;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = EXPECTED_GAIN_LOSS_PER_SHARE_COLUMN)),
            @AttributeOverride(name = "currency", column = @Column(name = EXPECTED_GAIN_LOSS_PER_SHARE_COLUMN
                    + "_currency", length = 10))
    })
    protected MonetaryAmount expectedGainLossPerShare;

    @Column(name = CAPITAL_GAINS_STATUS_COLUMN, length = 50)
    protected String capitalGainsStatus;

    public BaseStockLot(String symbol, Double sellableQuantity, MonetaryAmount estCostBasis,
            MonetaryAmount estMarketValue, MonetaryAmount expectedGainLoss,
            MonetaryAmount expectedGainLossPerShare, String capitalGainsStatus) {
        super(symbol);
        this.sellableQuantity = sellableQuantity;
        this.estCostBasis = estCostBasis;
        this.estMarketValue = estMarketValue;
        this.expectedGainLoss = expectedGainLoss;
        this.expectedGainLossPerShare = expectedGainLossPerShare;
        this.capitalGainsStatus = capitalGainsStatus;
    }
}
