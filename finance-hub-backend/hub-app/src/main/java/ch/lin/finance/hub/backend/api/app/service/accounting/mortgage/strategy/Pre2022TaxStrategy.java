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

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;

@Component
public class Pre2022TaxStrategy implements MortgageTaxDeductionStrategy {

    @Override
    public boolean isApplicable(LocalDate occupancyDate) {
        return occupancyDate.isBefore(LocalDate.of(2019, 10, 1));
    }

    @Override
    public Double calculateRate(ResidenceType type) {
        return 0.01; // 1.0%
    }

    @Override
    public Long calculateMaxAmount(ResidenceType type) {
        if (type == null) {
            return 400_000L;
        }
        return switch (type) {
            case LONG_TERM_CERTIFIED, ZEH -> 500_000L; // Max 50 million * 1% = 500,000
            default -> 400_000L; // Max 40 million * 1% = 400,000
        };
    }

    @Override
    public Integer calculateDurationYears(
            ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType propertyType) {
        // For occupancy prior to October 2019 (including the 8% consumption tax
        // "Specific Acquisition"
        // period between April 2014 and September 2019), statutory deduction duration
        // is uniformly 10 years.
        return 10;
    }

    @Override
    public long getMaxEligibleTotalIncome() {
        return 30_000_000L; // 30 million JPY threshold for occupancy prior to Reiwa 4 (2022)
    }
}
