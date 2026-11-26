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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssetSimulationYearlyResult extends BaseResult {
    private int year;
    private int age;
    private long projectedAnnualSalary;
    private long kouseiPensionContribution;
    private long accumulatedKouseiPension;
    private long expectedAnnualExpenses;
    private long netSavings;
    private long projectedNetWorth;
    private long projectedNisaBalance;
    private long projectedDcBalance;
    private long projectedCompanyStockBalance;
    private long projectedLiquidCash;
    private boolean isRetired;

    private long estimatedIncomeTax;
    private long estimatedInhabitantTax;
    private long estimatedSocialInsurance;
    private long netIncome;
    private long remainingCashThisYear;

    private long remainingMortgageBalance;
    private long annualMortgagePayment;
    private long trueNetWorth;
}
