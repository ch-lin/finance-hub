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

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;

/**
 * Mortgage tax deduction strategy for "Special Specific Acquisition" (特別特定取得)
 * covering the 10% consumption tax transition and COVID-19 relief period
 * (occupancy from October 1, 2019 to December 31, 2021).
 *
 * Statutory Rules:
 * 1. Deduction Rate: 1.0%.
 * 2. Balance Limit: 40,000,000 JPY for General residences, 50,000,000 JPY for
 * Certified Long-Term / Low-Carbon residences.
 * 3. Deduction Duration: 13 years for newly built residences; 10 years for
 * existing (used) residences.
 * 4. Total Income Ceiling: 30,000,000 JPY.
 *
 * TODO (Architectural Limitations & Future Enhancements):
 * 1. [Years 11-13 Deduction Formula]:
 * Under statutory Japanese tax law, the deduction limit for years 11 through 13
 * is the smaller of:
 * (a) Year-end mortgage balance * 1.0%
 * (b) (Building purchase price excluding tax * 2%) / 3
 * Currently, MortgageRecord only tracks total loan amount (totalLoanAmount) and
 * lacks a dedicated
 * building purchase price field. In this transitional implementation, years
 * 11-13 are projected
 * using year-end balance * 1.0% (capped at the statutory maximum of 400,000 /
 * 500,000 JPY).
 * Once the domain model is expanded with a building price field, this special
 * formula should be applied.
 * 2. [Non-taxable Used Property Transactions & Contract Date Rules]:
 * Used property transactions between private individuals are non-taxable and
 * legally limited to 10 years;
 * additionally, the 2021 COVID-19 extension requires meeting specific contract
 * signing deadlines.
 * The system currently determines duration solely by PropertyType (used
 * properties return 10 years).
 */
@Component
public class SpecialSpecificAcquisitionTaxStrategy implements MortgageTaxDeductionStrategy {

    private static final LocalDate START_DATE = LocalDate.of(2019, 10, 1);
    private static final LocalDate END_DATE = LocalDate.of(2022, 1, 1);

    @Override
    public boolean isApplicable(LocalDate occupancyDate) {
        if (occupancyDate == null) {
            return false;
        }
        return !occupancyDate.isBefore(START_DATE) && occupancyDate.isBefore(END_DATE);
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
    public Integer calculateDurationYears(PropertyType propertyType) {
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
        return 30_000_000L; // 30 million JPY threshold for occupancy prior to Reiwa 4 (2022)
    }
}
