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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NisaProjectionYearlyResult extends BaseResult {
    private int year;
    private Long projectedPrincipal;
    private Long projectedMarketValue;
    private Long projectedTotalGain;

    public NisaProjectionYearlyResult() {}

    public NisaProjectionYearlyResult(int year, Long projectedPrincipal, Long projectedMarketValue, Long projectedTotalGain) {
        this.year = year;
        this.projectedPrincipal = projectedPrincipal;
        this.projectedMarketValue = projectedMarketValue;
        this.projectedTotalGain = projectedTotalGain;
    }
}
