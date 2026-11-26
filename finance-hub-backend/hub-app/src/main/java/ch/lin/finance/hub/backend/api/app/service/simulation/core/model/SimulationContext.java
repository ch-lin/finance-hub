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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import lombok.Data;

@Data
public class SimulationContext implements Cloneable {

    private int currentYear;
    private int currentAge;

    private int year;
    private int age;
    private boolean isRetired;

    private UserProfile profile;
    private List<ChildProfile> children = new ArrayList<>();
    private Map<Long, Map<Integer, Long>> childEducationCostCache = Collections.emptyMap();
    private FinanceHubConfig config;
    private PensionConfigDto pensionConfig;
    private List<InsurancePolicyDto> insurancePolicies = new ArrayList<>();

    private long baseMonthlySalary;
    private long currentAnnualSalary;
    private long currentAnnualBonus;
    private int currentBonusPaymentCount;
    private long baselineMonthlyExpenses;
    private double healthInsuranceRate;
    private double pensionRate;
    private double employmentInsuranceRate;

    // YTD Actuals
    private long ytdGrossIncome;
    private long ytdBaseSalary;
    private long ytdNetAmount;
    private long ytdIncomeTax;
    private long ytdResidentTax;
    private long ytdHealthInsurance;
    private long ytdPension;
    private long ytdEmploymentInsurance;
    private boolean currentMonthSalaryRecorded;

    private long nisaBalance;
    private long accumulatedNisaPrincipal;
    /**
     * Tracks cumulative growth-quota (成長投資枠) principal for the ¥12,000,000
     * lifetime sub-cap.
     */
    private long accumulatedGrowthNisaPrincipal;
    private long nisaPrincipalToRecoverNextYear;
    private long growthNisaPrincipalToRecoverNextYear;
    private List<SimulationFund> nisaFunds = new ArrayList<>();

    private long dcBalance;
    private long dcMonthlyEmployerContribution;
    private List<SimulationFund> dcFunds = new ArrayList<>();

    private long idecoBalance;
    private List<SimulationFund> idecoFunds = new ArrayList<>();

    private long companyStockBalance;
    private long currentLiquidCash;
    private PreviousYearTaxRecord previousYearTaxRecord;

    // Pension tracking
    private long accumulatedKousei;
    private int currentKokuminMonths;
    private int currentKouseiMonths;

    // DC/iDeCo tracking
    private int dcContributionYears;
    private int idecoContributionYears;
    private LocalDate dcEnrollmentDate;
    private LocalDate idecoEnrollmentDate;

    // Arrays for mortgages
    private long[] mortgageBalances;
    private long[] mortgageMonthlyPayments;
    private double[] mortgageCurrentRates;
    private long[] mortgageCurrentExpectedPayments;
    private int[] mortgageMonthsSinceStart;

    // Records the single early repayment principal executed during simulation
    private long executedEarlyRepaymentPrincipal;

    @Override
    public SimulationContext clone() throws CloneNotSupportedException {
        SimulationContext copy = (SimulationContext) super.clone();
        if (this.mortgageBalances != null) {
            copy.mortgageBalances = this.mortgageBalances.clone();
        }
        if (this.mortgageMonthlyPayments != null) {
            copy.mortgageMonthlyPayments = this.mortgageMonthlyPayments.clone();
        }
        if (this.mortgageCurrentRates != null) {
            copy.mortgageCurrentRates = this.mortgageCurrentRates.clone();
        }
        if (this.mortgageCurrentExpectedPayments != null) {
            copy.mortgageCurrentExpectedPayments = this.mortgageCurrentExpectedPayments.clone();
        }
        if (this.mortgageMonthsSinceStart != null) {
            copy.mortgageMonthsSinceStart = this.mortgageMonthsSinceStart.clone();
        }

        copy.nisaFunds = new ArrayList<>(this.nisaFunds.size());
        for (SimulationFund fund : this.nisaFunds) {
            copy.nisaFunds.add(fund.clone());
        }

        copy.dcFunds = new ArrayList<>(this.dcFunds.size());
        for (SimulationFund fund : this.dcFunds) {
            copy.dcFunds.add(fund.clone());
        }

        copy.idecoFunds = new ArrayList<>(this.idecoFunds.size());
        for (SimulationFund fund : this.idecoFunds) {
            copy.idecoFunds.add(fund.clone());
        }

        if (this.insurancePolicies != null) {
            copy.insurancePolicies = new ArrayList<>(this.insurancePolicies);
        }

        if (this.children != null) {
            copy.children = new ArrayList<>(this.children.size());
            for (ChildProfile child : this.children) {
                // shallow copy for Entity is fine, or toBuilder().build() if needed
                // ChildProfile is an Entity, so shallow copy of the list is acceptable for now
                copy.children.add(child);
            }
        }

        // childEducationCostCache is guaranteed to be an immutable map (Collections.unmodifiableMap),
        // so sharing the reference across cloned contexts during simulation loops is both thread-safe and memory-efficient.
        copy.childEducationCostCache = this.childEducationCostCache;

        // Entities like profile and config are read-only during the loop, shallow copy
        // is fine
        return copy;
    }

    /**
     * Sets the education cost cache, wrapping it in Collections.unmodifiableMap
     * to guarantee immutability.
     */
    public void setChildEducationCostCache(Map<Long, Map<Integer, Long>> childEducationCostCache) {
        if (childEducationCostCache == null || childEducationCostCache.isEmpty()) {
            this.childEducationCostCache = Collections.emptyMap();
        } else {
            this.childEducationCostCache = Collections.unmodifiableMap(childEducationCostCache);
        }
    }

    public long getEducationCost(Long childProfileId, int year) {
        if (childEducationCostCache == null || childProfileId == null) {
            return 0L;
        }
        Map<Integer, Long> costMap = childEducationCostCache.get(childProfileId);
        if (costMap == null) {
            return 0L;
        }
        Long cost = costMap.get(year);
        return cost != null ? cost : 0L;
    }
}
