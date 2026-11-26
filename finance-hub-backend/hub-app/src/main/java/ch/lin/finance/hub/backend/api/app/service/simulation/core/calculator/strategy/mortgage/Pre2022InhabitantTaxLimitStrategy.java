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

public class Pre2022InhabitantTaxLimitStrategy implements InhabitantTaxLimitStrategy {

    private static final long FIXED_LIMIT = 136_500L;
    private static final double RATE = 0.07;

    @Override
    public long calculateLimit(long taxableIncomeForIncomeTax) {
        long percentageLimit = (long) (taxableIncomeForIncomeTax * RATE);
        return Math.min(FIXED_LIMIT, percentageLimit);
    }

    @Override
    public long getFixedLimit() {
        return FIXED_LIMIT;
    }
}
