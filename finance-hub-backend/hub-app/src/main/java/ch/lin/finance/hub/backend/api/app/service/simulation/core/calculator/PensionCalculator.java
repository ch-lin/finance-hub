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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;

@Component
public class PensionCalculator {

    /**
     * Calculates the projected Basic Pension (Kokumin Nenkin) amount.
     */
    public long calculateProjectedBasicPension(PensionConfigDto config, int currentKokuminMonths,
            int currentKouseiMonths, int additionalMonths, int pensionStartAge) {
        int projectedTotalMonths = currentKokuminMonths + currentKouseiMonths + additionalMonths;
        long maxMonths = config.getMaxBasicPensionMonths();
        long baseAmount = Math
                .round(config.getMaxBasicPensionAmount() * Math.min(maxMonths, projectedTotalMonths)
                        / (double) maxMonths);
        return Math.round(baseAmount * calculatePensionMultiplier(pensionStartAge));
    }

    /**
     * Validates and clamps the pension start age within the legal bounds (60-75).
     */
    public int getValidPensionStartAge(int requestedAge) {
        return Math.max(60, Math.min(75, requestedAge));
    }

    /**
     * Calculates the multiplier for early (before 65) or late (after 65) pension
     * withdrawal.
     * 60-64: -0.4% per month early (max -24%)
     * 66-75: +0.7% per month late (max +84%)
     */
    public double calculatePensionMultiplier(int pensionStartAge) {
        int validAge = getValidPensionStartAge(pensionStartAge);
        if (validAge == 65) {
            return 1.0;
        } else if (validAge < 65) {
            int monthsEarly = (65 - validAge) * 12;
            return 1.0 - (monthsEarly * 0.004);
        } else {
            int monthsLate = (validAge - 65) * 12;
            return 1.0 + (monthsLate * 0.007);
        }
    }

    /**
     * Calculates the projected Kousei Pension benefit increment earned from the
     * annual salary and bonus of that year.
     * This is useful for dynamic year-by-year simulations where salary changes.
     */
    public long calculateYearlyKouseiBenefitIncrement(PensionConfigDto config, long annualBaseSalary, long annualBonus,
            int bonusPaymentCount) {
        long actualMonthly = annualBaseSalary / 12;
        long cappedMonthly = Math.min(actualMonthly, config.getKouseiSalaryCapMonthly());
        double multiplier = config.getKouseiMultiplierPerMille().doubleValue() / 1000.0;
        long baseBenefit = Math.round(cappedMonthly * multiplier * 12);

        long bonusBenefit = 0;
        if (annualBonus > 0 && bonusPaymentCount > 0) {
            long bonusPerPayment = annualBonus / bonusPaymentCount;
            long cappedBonusPerPayment = Math.min(bonusPerPayment, config.getKouseiBonusCapPerPayment());
            bonusBenefit = Math.round(cappedBonusPerPayment * multiplier * bonusPaymentCount);
        }

        return baseBenefit + bonusBenefit;
    }
}
