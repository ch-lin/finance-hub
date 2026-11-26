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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;

import java.time.YearMonth;
import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.utils.MortgageCalculatorUtil;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;

public final class OptimizationUtils {

    private OptimizationUtils() {
    }

    public record MortgageYearRange(int minYear, int maxYear) {
    }

    public static MortgageYearRange calculateMortgageYearRange(List<MortgageRecord> mortgages, int currentYear) {
        if (mortgages == null || mortgages.isEmpty()) {
            return null;
        }
        int minYear = currentYear;
        int endYear = currentYear;
        for (MortgageRecord m : mortgages) {
            int reducedMonths = MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m,
                    YearMonth.of(currentYear, 12));
            int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths() - reducedMonths).getYear();
            if (mEndYear > endYear) {
                endYear = mEndYear;
            }
        }
        return new MortgageYearRange(minYear, endYear + 1); // +1 allows paying off at the very end
    }
}
