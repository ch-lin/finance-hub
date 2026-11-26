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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage;

public interface InhabitantTaxLimitStrategy {

    /**
     * Calculates the upper limit of the mortgage deduction for inhabitant tax.
     * 
     * @param taxableIncomeForIncomeTax the total taxable income for income tax
     * @return the limit amount
     */
    long calculateLimit(long taxableIncomeForIncomeTax);

    /**
     * Gets the statutory absolute fixed limit amount (used for selecting the
     * strategy with the maximum limit when comparing multiple mortgages).
     * 
     * @return the fixed limit amount
     */
    long getFixedLimit();
}
