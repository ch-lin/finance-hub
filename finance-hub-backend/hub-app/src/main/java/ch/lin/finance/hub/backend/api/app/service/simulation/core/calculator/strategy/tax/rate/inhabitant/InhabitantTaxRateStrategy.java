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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant;

/**
 * Strategy interface for calculating individual inhabitant tax (個人住民税)
 * according to Japanese Local Tax Act (地方税法) and Forest Environmental Tax Act.
 */
public interface InhabitantTaxRateStrategy {

    /**
     * Calculates the total inhabitant tax amount (住民税総額 = 所得割額 + 均等割額・森林環境税).
     *
     * @param taxableIncome the statutory inhabitant taxable income (課税所得金額, typically rounded down to 1,000 JPY)
     * @param totalIncome   the total income before deductions (合計所得金額: 給与所得 or 公的年金等雑所得),
     *                      used to determine per-capita flat levy non-taxable threshold (均等割非課税限度額).
     *                      For retirement income (退職所得) which is separate taxation exempt from flat levy
     *                      under Local Tax Act Article 50-2 and 328, pass 0L.
     * @return the calculated total inhabitant tax amount
     */
    long calculateInhabitantTax(long taxableIncome, long totalIncome);
}
