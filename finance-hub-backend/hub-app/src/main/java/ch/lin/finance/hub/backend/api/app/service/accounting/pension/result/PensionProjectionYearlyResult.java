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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing the projected pension values for a given
 * number of additional working years.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class PensionProjectionYearlyResult extends BaseResult {
    /**
     * The number of extra years worked from the current date.
     */
    private int yearsAdditionalWorked;

    /**
     * The projected annual basic pension (Kokumin).
     */
    private long projectedBasicPensionAnnual;

    /**
     * The projected annual employees' pension (Kousei).
     */
    private long projectedKouseiPensionAnnual;

    /**
     * The total projected annual pension.
     */
    private long projectedTotalPensionAnnual;

    /**
     * The estimated lifetime total payout based on life expectancy.
     */
    private long projectedLifetimeTotal;
}
