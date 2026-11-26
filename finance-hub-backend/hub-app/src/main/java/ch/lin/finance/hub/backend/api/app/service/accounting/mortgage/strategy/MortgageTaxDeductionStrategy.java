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

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;

public interface MortgageTaxDeductionStrategy {

    /**
     * Determine if this strategy applies to the given occupancy date.
     */
    boolean isApplicable(LocalDate occupancyDate);

    /**
     * Calculate the tax deduction rate based on the residence type.
     */
    Double calculateRate(ResidenceType type);

    /**
     * Calculate the maximum eligible tax deduction amount based on the residence type.
     */
    Long calculateMaxAmount(ResidenceType type);

    /**
     * Calculate the duration of the tax deduction based on the property type.
     */
    Integer calculateDurationYears(ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType propertyType);

    /**
     * Calculate the maximum eligible total income threshold for mortgage tax deduction (租稅特別措置法第41條第1項).
     * If the taxpayer's total employment income exceeds this threshold, the mortgage tax deduction is 0.
     *
     * @return the maximum eligible total income in JPY
     */
    long getMaxEligibleTotalIncome();
}
