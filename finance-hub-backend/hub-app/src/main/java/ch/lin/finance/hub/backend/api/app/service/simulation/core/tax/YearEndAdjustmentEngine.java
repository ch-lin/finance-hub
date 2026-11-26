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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;

import org.springframework.stereotype.Component;

/**
 * Calculates the exact year-end adjustment (年末調整) for the first year of
 * simulation.
 * It compares the theoretical True Annual Tax (calculated by
 * ActiveTaxCalculationEngine)
 * with the YTD Actuals to determine the exact remaining net income to be
 * received.
 */
@Component
public class YearEndAdjustmentEngine {

    /**
     * Calculates the remaining net income for the rest of the year.
     *
     * @param trueAnnualGross           The theoretical true annual gross income
     *                                  (Base + Bonus).
     * @param trueAnnualIncomeTax       The theoretical true annual income tax.
     * @param trueAnnualInhabitantTax   The theoretical true annual inhabitant tax.
     * @param trueAnnualSocialInsurance The theoretical true annual social
     *                                  insurance.
     * @param ytdGross                  Actual YTD gross income.
     * @param ytdIncomeTax              Actual YTD income tax paid.
     * @param ytdResidentTax            Actual YTD resident tax paid.
     * @param ytdSocialInsurance        Actual YTD social insurance paid.
     * @return The remaining net income to be received for the rest of the year.
     */
    public YearEndAdjustmentResult calculateYearEndAdjustment(
            long trueAnnualGross,
            long trueAnnualIncomeTax,
            long trueAnnualInhabitantTax,
            long trueAnnualSocialInsurance,
            long ytdGross,
            long ytdIncomeTax,
            long ytdResidentTax,
            long ytdSocialInsurance) {

        long remainingGross = trueAnnualGross - ytdGross;
        // Tax liabilities or refunds (negative means we overpaid and get a refund)
        long remainingIncomeTax = trueAnnualIncomeTax - ytdIncomeTax;
        long remainingInhabitantTax = trueAnnualInhabitantTax - ytdResidentTax;
        long remainingSocialInsurance = trueAnnualSocialInsurance - ytdSocialInsurance;

        // Remaining net income is whatever gross is left minus whatever taxes are still
        // owed
        long remainingNetIncome = remainingGross - remainingIncomeTax - remainingInhabitantTax
                - remainingSocialInsurance;

        return new YearEndAdjustmentResult(
                remainingNetIncome,
                remainingIncomeTax,
                remainingInhabitantTax,
                remainingSocialInsurance);
    }
}
