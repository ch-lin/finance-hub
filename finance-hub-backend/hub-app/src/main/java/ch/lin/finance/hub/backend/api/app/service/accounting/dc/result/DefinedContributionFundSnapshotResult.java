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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DefinedContributionFundSnapshotResult extends BaseResult {
    private Long id;
    private String commodityType;
    private String fundName;
    private String fundCode;
    private Double units;
    private Double averageCost;
    private Double currentPrice;
    private Long totalPrincipal;
    private Long currentMarketValue;
    private Long unrealizedGainLoss;
    private Double expectedReturnRate;
    private Long monthlyContributionAmount;
    private Double allocationPercentage;

    public static DefinedContributionFundSnapshotResult fromEntity(DefinedContributionFundSnapshot entity) {
        if (entity == null) {
            return null;
        }
        DefinedContributionFundSnapshotResult result = new DefinedContributionFundSnapshotResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setId(entity.getId());
        result.setCommodityType(entity.getCommodityType());
        result.setFundName(entity.getFundName());
        result.setFundCode(entity.getFundCode());
        result.setUnits(entity.getUnits());
        result.setAverageCost(entity.getAverageCost());
        result.setCurrentPrice(entity.getCurrentPrice());
        result.setTotalPrincipal(entity.getTotalPrincipal());
        result.setCurrentMarketValue(entity.getCurrentMarketValue());
        result.setUnrealizedGainLoss(entity.getUnrealizedGainLoss());
        result.setExpectedReturnRate(entity.getExpectedReturnRate());
        result.setMonthlyContributionAmount(entity.getMonthlyContributionAmount());
        result.setAllocationPercentage(entity.getAllocationPercentage());
        return result;
    }
}
