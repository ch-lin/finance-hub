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

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant.InhabitantTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentType;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

@Service
public class ActiveTaxCalculationEngine {

    private final SpouseDeductionEngine spouseDeductionEngine;
    private final DependentDeductionEngine dependentDeductionEngine;
    private final BasicDeductionStrategyFactory basicDeductionStrategyFactory;
    private final RetirementTaxStrategyFactory retirementTaxStrategyFactory;
    private final EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory;
    private final IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory;
    private final InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory;

    @Autowired
    public ActiveTaxCalculationEngine(SpouseDeductionEngine spouseDeductionEngine,
            DependentDeductionEngine dependentDeductionEngine,
            BasicDeductionStrategyFactory basicDeductionStrategyFactory,
            RetirementTaxStrategyFactory retirementTaxStrategyFactory,
            EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory,
            IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory,
            InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory) {
        this.spouseDeductionEngine = spouseDeductionEngine;
        this.dependentDeductionEngine = dependentDeductionEngine;
        this.basicDeductionStrategyFactory = basicDeductionStrategyFactory;
        this.retirementTaxStrategyFactory = retirementTaxStrategyFactory;
        this.employmentIncomeDeductionStrategyFactory = employmentIncomeDeductionStrategyFactory;
        this.incomeTaxRateStrategyFactory = incomeTaxRateStrategyFactory;
        this.inhabitantTaxRateStrategyFactory = inhabitantTaxRateStrategyFactory;
    }

    public TaxCalculationResult calculate(SimulationContext context, long grossBaseSalary, long annualBonus,
            int bonusPaymentCount,
            long annualIdecoContribution, long annualDcMatchingContribution,
            MortgageYearlyResult mortgageResult) {
        long yearEndDeduction = mortgageResult.yearEndDeduction();
        long kakuteiDeduction = mortgageResult.kakuteiDeduction();

        double healthInsuranceRate = context.getHealthInsuranceRate();
        double pensionRate = context.getPensionRate();
        double employmentInsuranceRate = context.getEmploymentInsuranceRate();
        UserProfile userProfile = context.getProfile();
        int simulationYear = context.getYear();

        // 1. Social Insurance (with Statutory Caps)
        long monthlySalary = grossBaseSalary / 12;
        long healthBase = Math.min(monthlySalary, 1_390_000L);
        long pensionBase = Math.min(monthlySalary, context.getPensionConfig().getKouseiSalaryCapMonthly());

        long annualHealth = (long) (healthBase * healthInsuranceRate * 12);
        long annualPension = (long) (pensionBase * pensionRate * 12);
        long annualEmp = (long) (grossBaseSalary * employmentInsuranceRate);

        if (annualBonus > 0 && bonusPaymentCount > 0) {
            long bonusPerPayment = annualBonus / bonusPaymentCount;
            long healthBonusBase = Math.min(annualBonus, 5_730_000L); // Cap is annual for health
            // Cap is per payment for pension
            long pensionBonusBasePerPayment = Math.min(bonusPerPayment,
                    context.getPensionConfig().getKouseiBonusCapPerPayment());

            annualHealth += (long) (healthBonusBase * healthInsuranceRate);
            annualPension += (long) (pensionBonusBasePerPayment * pensionRate * bonusPaymentCount);
            annualEmp += (long) (annualBonus * employmentInsuranceRate);
        }

        long socialInsurance = annualHealth + annualPension + annualEmp;

        // 2. Employment Income Deduction (給与所得控除)
        long grossTotalSalary = grossBaseSalary + annualBonus;
        long employmentIncomeDeduction = employmentIncomeDeductionStrategyFactory
                .getStrategy(simulationYear)
                .calculateDeduction(grossTotalSalary);

        // 3. Gross Employment Income (給与所得)
        long employmentIncome = grossTotalSalary - employmentIncomeDeduction;
        long basicDeductionIncomeTax = basicDeductionStrategyFactory.getIncomeTaxStrategy(simulationYear)
                .calculateIncomeTaxDeduction(employmentIncome);
        long basicDeductionInhabitantTax = basicDeductionStrategyFactory.getInhabitantTaxStrategy(simulationYear)
                .calculateInhabitantTaxDeduction(employmentIncome);

        // 4. Deductions
        long idecoDeduction = annualIdecoContribution;
        long dcMatchingDeduction = annualDcMatchingContribution;
        long retirementDeduction = idecoDeduction + dcMatchingDeduction;

        LocalDate simulationYearEnd = LocalDate.of(simulationYear, 12, 31);

        long spouseDeductionIncome = 0L;
        long spouseDeductionInhabitant = 0L;
        long dependentDeductionIncome = 0L;
        long dependentDeductionInhabitant = 0L;

        if (userProfile != null && userProfile.getFamilyMembers() != null) {
            for (Dependent fm : userProfile.getFamilyMembers()) {
                if (fm.getMemberType() == DependentType.SPOUSE) {
                    spouseDeductionIncome += spouseDeductionEngine.calculateIncomeTaxDeduction(grossTotalSalary, fm,
                            simulationYearEnd);
                    spouseDeductionInhabitant += spouseDeductionEngine.calculateInhabitantTaxDeduction(grossTotalSalary,
                            fm, simulationYearEnd);
                } else if (fm.getMemberType() == DependentType.DEPENDENT) {
                    dependentDeductionIncome += dependentDeductionEngine.calculateIncomeTaxDeduction(context, fm,
                            simulationYearEnd);
                    dependentDeductionInhabitant += dependentDeductionEngine.calculateInhabitantTaxDeduction(context,
                            fm,
                            simulationYearEnd);
                }
            }
        }

        long generalLifePremium = 0L;
        long nursingCarePremium = 0L;
        long earthquakePremium = 0L;

        if (userProfile != null && context.getInsurancePolicies() != null) {
            for (InsurancePolicyDto policy : context.getInsurancePolicies()) {
                if (policy.annualPremium() != null) {
                    int activeMonths = 12;

                    if (policy.endDate() != null) {
                        if (policy.endDate().getYear() < simulationYear) {
                            continue;
                        } else if (policy.endDate().getYear() == simulationYear) {
                            activeMonths = policy.endDate().getMonthValue();
                        }
                    }

                    long premium = policy.annualPremium();
                    if (activeMonths < 12) {
                        premium = Math.round((premium / 12.0) * activeMonths);
                    }

                    switch (policy.policyType()) {
                        case LIFE, ENDOWMENT ->
                            generalLifePremium += premium;
                        case NURSING_CARE, HEALTH, CANCER, MEDICAL ->
                            nursingCarePremium += premium;
                        case EARTHQUAKE ->
                            earthquakePremium += premium;
                        default -> {
                        } // PENSION, FIRE, AUTO, OTHER are ignored for now or handled separately
                    }
                }
            }
        }

        long lifeDeductionIncomeTax = Math.min(120_000L,
                calculateLifeInsuranceDeductionIncomeTax(generalLifePremium)
                        + calculateLifeInsuranceDeductionIncomeTax(nursingCarePremium));

        long lifeDeductionInhabitantTax = Math.min(70_000L,
                calculateLifeInsuranceDeductionInhabitantTax(generalLifePremium)
                        + calculateLifeInsuranceDeductionInhabitantTax(nursingCarePremium));

        long eqDeductionIncomeTax = Math.min(50_000L, earthquakePremium);
        long eqDeductionInhabitantTax = Math.min(25_000L, earthquakePremium / 2);

        // Total deductions
        long totalDeductionsIncomeTax = socialInsurance + retirementDeduction + basicDeductionIncomeTax
                + spouseDeductionIncome + dependentDeductionIncome + lifeDeductionIncomeTax + eqDeductionIncomeTax;

        long totalDeductionsInhabitantTax = socialInsurance + retirementDeduction + basicDeductionInhabitantTax
                + spouseDeductionInhabitant + dependentDeductionInhabitant + lifeDeductionInhabitantTax
                + eqDeductionInhabitantTax;

        // 5. Taxable Income
        long taxableIncomeForIncomeTax = Math.max(0, employmentIncome - totalDeductionsIncomeTax);
        // Round down to nearest 1,000 JPY
        taxableIncomeForIncomeTax = (taxableIncomeForIncomeTax / 1000) * 1000;

        long taxableIncomeForInhabitantTax = Math.max(0, employmentIncome - totalDeductionsInhabitantTax);
        taxableIncomeForInhabitantTax = (taxableIncomeForInhabitantTax / 1000) * 1000;

        // 6. Calculate Taxes
        long baseIncomeTax = incomeTaxRateStrategyFactory.getStrategy(simulationYear)
                .calculateIncomeTax(taxableIncomeForIncomeTax);

        // --- Apply Mortgage Tax Deduction (directly against income tax) ---
        // Under Article 41, Paragraph 1 of the Act on Special Measures Concerning
        // Taxation,
        // mortgage tax deduction is strictly disqualified (reduced to 0) if the
        // taxpayer's
        // total employment income exceeds the statutory threshold for the occupancy
        // year
        // (e.g., 20,000,000 JPY for 2022 onwards, 30,000,000 JPY prior to 2022).
        if ((yearEndDeduction > 0 || kakuteiDeduction > 0)
                && employmentIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) {
            yearEndDeduction = 0L;
            kakuteiDeduction = 0L;
        }

        long actualYearEndDeduction = Math.min(baseIncomeTax, yearEndDeduction);
        baseIncomeTax -= actualYearEndDeduction;

        long actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction);

        // Reconstruction special income tax (2.1% of income tax)
        long incomeTax = baseIncomeTax + (long) (baseIncomeTax * 0.021);

        // Inhabitant tax (statutory rate strategy: income levy + per-capita flat levy)
        long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(simulationYear)
                .calculateInhabitantTax(taxableIncomeForInhabitantTax, employmentIncome);

        PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
        if (prevTax != null && prevTax.unusedMortgageDeductionForInhabitantTax() > 0) {
            long inhabitantTaxCredit = Math.min(inhabitantTax, prevTax.unusedMortgageDeductionForInhabitantTax());
            inhabitantTax -= inhabitantTaxCredit;
        }

        long totalDeduction = yearEndDeduction + kakuteiDeduction;
        long totalUsed = actualYearEndDeduction + actualKakuteiRefund;
        long remainingMortgageDeduction = totalDeduction - totalUsed;
        long unusedForInhabitantTax = 0L;
        if (remainingMortgageDeduction > 0) {
            long maxInhabitantTaxDeduction = mortgageResult.inhabitantTaxLimitStrategy()
                    .calculateLimit(taxableIncomeForIncomeTax);
            unusedForInhabitantTax = Math.min(remainingMortgageDeduction, maxInhabitantTaxDeduction);
        }

        // Package cross-year tax record for the next year
        context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(actualKakuteiRefund, unusedForInhabitantTax, 0L));

        // 7. Net Income
        long netIncome = grossTotalSalary - socialInsurance - incomeTax - inhabitantTax;

        return TaxCalculationResult.builder()
                .incomeTax(incomeTax)
                .inhabitantTax(inhabitantTax)
                .socialInsurance(socialInsurance)
                .netIncome(netIncome)
                .build();
    }

    private long calculateLifeInsuranceDeductionIncomeTax(long premium) {
        if (premium <= 20_000) {
            return premium;
        } else if (premium <= 40_000) {
            return (premium / 2) + 10_000;
        } else if (premium <= 80_000) {
            return (premium / 4) + 20_000;
        } else {
            return 40_000;
        }
    }

    private long calculateLifeInsuranceDeductionInhabitantTax(long premium) {
        if (premium <= 12_000) {
            return premium;
        } else if (premium <= 32_000) {
            return (premium / 2) + 6_000;
        } else if (premium <= 56_000) {
            return (premium / 4) + 14_000;
        } else {
            return 28_000;
        }
    }

    public TaxCalculationResult calculateRetirementTax(SimulationContext context, long retirementIncome,
            int contributionYears) {
        long deduction;
        if (contributionYears <= 20) {
            deduction = 400_000L * contributionYears;
            deduction = Math.max(800_000L, deduction);
        } else {
            deduction = 8_000_000L + 700_000L * (contributionYears - 20);
        }

        long taxableIncome = retirementTaxStrategyFactory.getStrategy(contributionYears)
                .calculateTaxableIncome(retirementIncome, deduction);

        if (taxableIncome <= 0) {
            return TaxCalculationResult.builder()
                    .incomeTax(0)
                    .inhabitantTax(0)
                    .socialInsurance(0)
                    .netIncome(retirementIncome)
                    .build();
        }

        long baseIncomeTax = incomeTaxRateStrategyFactory.getStrategy(context.getYear())
                .calculateIncomeTax(taxableIncome);
        long incomeTax = baseIncomeTax + (long) (baseIncomeTax * 0.021);

        // Retirement income is subject to separate taxation (地方税法第50条の2 / 第328条)
        // and is legally exempt from per-capita flat levy (passed as 0L).
        long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(context.getYear())
                .calculateInhabitantTax(taxableIncome, 0L);

        return TaxCalculationResult.builder()
                .incomeTax(incomeTax)
                .inhabitantTax(inhabitantTax)
                .socialInsurance(0)
                .netIncome(retirementIncome - incomeTax - inhabitantTax)
                .build();
    }

    public TaxCalculationResult calculatePensionTax(
            SimulationContext context,
            long pensionIncome,
            int age,
            MortgageYearlyResult mortgageResult) {
        long publicPensionDeduction;
        if (pensionIncome <= 4_100_000) {
            publicPensionDeduction = (long) (pensionIncome * 0.25) + 275_000;
        } else if (pensionIncome <= 7_700_000) {
            publicPensionDeduction = (long) (pensionIncome * 0.15) + 685_000;
        } else {
            publicPensionDeduction = (long) (pensionIncome * 0.05) + 1_455_000;
        }

        long minDeduction = (age >= 65) ? 1_100_000L : 600_000L;
        publicPensionDeduction = Math.max(publicPensionDeduction, minDeduction);

        // Statutory miscellaneous income from public pensions (公的年金等に係る雑所得) under
        // Income Tax Act Article 35
        // (所得税法第35条), serving as total income (合計所得金額) before deductions for retirees.
        long miscellaneousIncome = Math.max(0, pensionIncome - publicPensionDeduction);

        // Estimate Social Insurance for retirees (approx 9.5% of pension income)
        long socialInsurance = (long) (pensionIncome * 0.095);

        long basicDeductionIncomeTax = basicDeductionStrategyFactory.getIncomeTaxStrategy(context.getYear())
                .calculateIncomeTaxDeduction(miscellaneousIncome);
        long basicDeductionInhabitantTax = basicDeductionStrategyFactory.getInhabitantTaxStrategy(context.getYear())
                .calculateInhabitantTaxDeduction(miscellaneousIncome);

        // Deductions
        long totalDeductionsIncomeTax = socialInsurance + basicDeductionIncomeTax;
        long totalDeductionsInhabitantTax = socialInsurance + basicDeductionInhabitantTax;

        long taxableIncomeForIncomeTax = Math.max(0, miscellaneousIncome - totalDeductionsIncomeTax);
        taxableIncomeForIncomeTax = (taxableIncomeForIncomeTax / 1000) * 1000;

        long taxableIncomeForInhabitantTax = Math.max(0, miscellaneousIncome - totalDeductionsInhabitantTax);
        taxableIncomeForInhabitantTax = (taxableIncomeForInhabitantTax / 1000) * 1000;

        long baseIncomeTax = incomeTaxRateStrategyFactory.getStrategy(context.getYear())
                .calculateIncomeTax(taxableIncomeForIncomeTax);
        long incomeTax = baseIncomeTax + (long) (baseIncomeTax * 0.021);

        // Inhabitant tax on public pension (income levy + per-capita flat levy)
        long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(context.getYear())
                .calculateInhabitantTax(taxableIncomeForInhabitantTax, miscellaneousIncome);

        long netIncome = pensionIncome - socialInsurance - incomeTax - inhabitantTax;

        if (mortgageResult != null) {
            long kakuteiDeduction = mortgageResult.kakuteiDeduction();

            // Statutory income ceiling check
            if (kakuteiDeduction > 0
                    && miscellaneousIncome > mortgageResult.maxTotalIncomeForMortgageDeduction()) {
                kakuteiDeduction = 0L;
            }

            PreviousYearTaxRecord prevTax = context.getPreviousYearTaxRecord();
            long usedByWorking = (prevTax != null) ? prevTax.pendingKakuteiShinkokuRefund() : 0L;
            long remainingKakuteiDeduction = Math.max(0, kakuteiDeduction - usedByWorking);

            if (kakuteiDeduction > 0 || usedByWorking > 0) {
                long usedByPension = Math.min(baseIncomeTax, remainingKakuteiDeduction);
                long totalRefund = usedByWorking + usedByPension;

                long remainingDeduction = remainingKakuteiDeduction - usedByPension;
                long unusedForInhabitantTax = 0L;
                if (remainingDeduction > 0) {
                    long maxInhabitantTaxDeduction = mortgageResult.inhabitantTaxLimitStrategy()
                            .calculateLimit(taxableIncomeForIncomeTax);
                    unusedForInhabitantTax = Math.min(remainingDeduction, maxInhabitantTaxDeduction);
                }

                long pendingAdjustment = (prevTax != null) ? prevTax.pendingWorkingSegmentTaxAdjustment() : 0L;

                context.setPreviousYearTaxRecord(new PreviousYearTaxRecord(
                        totalRefund,
                        unusedForInhabitantTax,
                        pendingAdjustment));
            }
        }

        return TaxCalculationResult.builder()
                .incomeTax(incomeTax)
                .inhabitantTax(inhabitantTax)
                .socialInsurance(socialInsurance)
                .netIncome(netIncome)
                .build();
    }
}
