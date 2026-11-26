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
package ch.lin.finance.hub.backend.api.controller.accounting.dc.dto;

import lombok.Data;

@Data
public class DefinedContributionFundSnapshotResponse {
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
}
