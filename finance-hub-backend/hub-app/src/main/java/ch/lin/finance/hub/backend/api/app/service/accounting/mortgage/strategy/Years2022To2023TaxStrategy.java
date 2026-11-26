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
public class Years2022To2023TaxStrategy implements MortgageTaxDeductionStrategy {

    @Override
    public boolean isApplicable(LocalDate occupancyDate) {
        return !occupancyDate.isBefore(LocalDate.of(2022, 1, 1))
                && occupancyDate.isBefore(LocalDate.of(2024, 1, 1));
    }

    @Override
    public Double calculateRate(ResidenceType type) {
        return 0.007; // 0.7%
    }

    @Override
    public Long calculateMaxAmount(ResidenceType type) {
        if (type == null) {
            return 210_000L;
        }
        return switch (type) {
            case LONG_TERM_CERTIFIED -> 350_000L; // 50m * 0.7%
            case ZEH -> 315_000L; // 45m * 0.7%
            case ENERGY_SAVING -> 280_000L; // 40m * 0.7%
            case GENERAL -> 210_000L; // 30m * 0.7%
        };
    }

    @Override
    public Integer calculateDurationYears(ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType propertyType) {
        if (propertyType == null) {
            return 10;
        }
        return switch (propertyType) {
            case NEW_DETACHED_HOUSE, NEW_APARTMENT -> 13;
            default -> 10;
        };
    }

    @Override
    public long getMaxEligibleTotalIncome() {
        return 20_000_000L; // 令和4年以後入居為 2,000 萬円
    }
}
