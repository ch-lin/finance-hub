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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement;

/**
 * Strategy interface for calculating taxable retirement income (課税退職所得金額).
 * <p>
 * Under Japanese Income Tax Law (所得税法第30条) and General Law of National Taxes (国税通則法第118条第1項),
 * the calculated taxable retirement income is rounded down to the nearest 1,000 yen (1,000円未満切捨て).
 */
public interface RetirementTaxStrategy {

    /**
     * Calculates the statutory taxable retirement income (課税退職所得金額).
     *
     * @param retirementIncome the total retirement gross income / benefits received (退職手当等の収入金額)
     * @param deduction the statutory retirement income deduction amount (退職所得控除額)
     * @return taxable retirement income rounded down to nearest 1,000 yen (1,000円未満切捨て)
     */
    long calculateTaxableIncome(long retirementIncome, long deduction);
}
