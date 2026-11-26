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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TaxStrategyFactory {

    private final List<MortgageTaxDeductionStrategy> strategies;

    public TaxStrategyFactory(List<MortgageTaxDeductionStrategy> strategies) {
        this.strategies = strategies;
    }

    public MortgageTaxDeductionStrategy getStrategy(LocalDate occupancyDate) {
        if (occupancyDate == null) {
            return null; // or throw an exception if required
        }
        
        return strategies.stream()
                .filter(strategy -> strategy.isApplicable(occupancyDate))
                .findFirst()
                .orElse(null); // Return null if no strategy matches (e.g., future dates without rules yet)
    }
}
