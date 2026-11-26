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
package ch.lin.finance.hub.backend.api.domain.model.accounting.dc;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = DefinedContributionFundSnapshot.TABLE_NAME, indexes = {
        @Index(name = DefinedContributionFundSnapshot.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
})
@Getter
@EqualsAndHashCode(of = { "accountSnapshot", "fundCode" }, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefinedContributionFundSnapshot extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "dc_fund_snapshot";
    public static final String ID_INDEX = "dc_fund_snapshot_id_index";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_snapshot_id", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    @com.fasterxml.jackson.annotation.JsonBackReference
    private DefinedContributionAccountSnapshot accountSnapshot;

    @Column(name = "commodity_type")
    private String commodityType; // e.g. 國內其他資產, 海外股票 (can be null)

    @Setter
    @NotNull
    @Column(name = "fund_code", nullable = false)
    private String fundCode; // e.g. Benefit Systems scd code (mandatory)

    @NotNull
    @Column(name = "fund_name", nullable = false)
    private String fundName;

    @ColumnDefault("0")
    @Column(name = "units")
    private double units = 0.0;

    @ColumnDefault("0")
    @Column(name = "average_cost")
    private double averageCost = 0.0;

    @ColumnDefault("0")
    @Column(name = "current_price")
    private double currentPrice = 0.0;

    @ColumnDefault("0")
    @Column(name = "total_principal")
    private long totalPrincipal = 0L;

    @ColumnDefault("0")
    @Column(name = "current_market_value")
    private long currentMarketValue = 0L;

    @ColumnDefault("0")
    @Column(name = "unrealized_gain_loss")
    private long unrealizedGainLoss = 0L;

    @Column(name = "expected_return_rate")
    private Double expectedReturnRate;

    @ColumnDefault("0")
    @Column(name = "monthly_contribution_amount")
    private long monthlyContributionAmount = 0L;

    @ColumnDefault("0")
    @Column(name = "allocation_percentage")
    private double allocationPercentage = 0.0;

    public DefinedContributionFundSnapshot(String commodityType, String fundName, String fundCode, double units,
            double averageCost, double currentPrice, long totalPrincipal, long currentMarketValue,
            long unrealizedGainLoss) {
        this(commodityType, fundName, fundCode, units, averageCost, currentPrice, totalPrincipal, currentMarketValue,
                unrealizedGainLoss, 0L, 0.0);
    }

    public DefinedContributionFundSnapshot(String commodityType, String fundName, String fundCode, double units,
            double averageCost, double currentPrice, long totalPrincipal, long currentMarketValue,
            long unrealizedGainLoss, long monthlyContributionAmount, double allocationPercentage) {
        this.commodityType = commodityType;
        this.fundName = fundName;
        this.fundCode = fundCode;
        this.units = units;
        this.averageCost = averageCost;
        this.currentPrice = currentPrice;
        this.totalPrincipal = totalPrincipal;
        this.currentMarketValue = currentMarketValue;
        this.unrealizedGainLoss = unrealizedGainLoss;
        this.expectedReturnRate = null;
        this.monthlyContributionAmount = monthlyContributionAmount;
        this.allocationPercentage = allocationPercentage;
    }

    public void updateExpectedReturnRate(Double expectedReturnRate) {
        this.expectedReturnRate = expectedReturnRate;
    }

    public void updateAllocation(long contributionAmount, double allocationPercentage) {
        this.monthlyContributionAmount = contributionAmount;
        this.allocationPercentage = allocationPercentage;
    }

    /**
     * Merges factual fields from an ALLOCATION page facet.
     * Directly overwrites contribution and allocation facts.
     */
    public void mergeAllocationFacts(DefinedContributionFundSnapshot incoming) {
        this.monthlyContributionAmount = incoming.monthlyContributionAmount;
        this.allocationPercentage = incoming.allocationPercentage;
        this.commodityType = incoming.commodityType;
        this.fundName = incoming.fundName;
    }

    /**
     * Merges factual fields from a STATUS page facet.
     * Directly overwrites holding balance facts.
     */
    public void mergeStatusFacts(DefinedContributionFundSnapshot incoming) {
        this.units = incoming.units;
        this.averageCost = incoming.averageCost;
        this.currentPrice = incoming.currentPrice;
        this.totalPrincipal = incoming.totalPrincipal;
        this.currentMarketValue = incoming.currentMarketValue;
        this.unrealizedGainLoss = incoming.unrealizedGainLoss;
        this.commodityType = incoming.commodityType;
        this.fundName = incoming.fundName;
    }
}
