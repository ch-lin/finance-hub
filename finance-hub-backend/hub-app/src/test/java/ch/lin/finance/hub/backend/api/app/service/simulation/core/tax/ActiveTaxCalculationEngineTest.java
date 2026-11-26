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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.Post2022InhabitantTaxLimitStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage.ZeroInhabitantTaxLimitStrategy;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax.PreviousYearTaxRecord;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic.BasicDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment.EmploymentIncomeDeductionStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.income.IncomeTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.rate.inhabitant.InhabitantTaxRateStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.retirement.RetirementTaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.MortgageYearlyResult;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

class ActiveTaxCalculationEngineTest {

    private final SpouseDeductionEngine spouseEngine = new SpouseDeductionEngine();
    private final DependentDeductionEngine dependentEngine = new DependentDeductionEngine();
    private final BasicDeductionStrategyFactory basicDeductionStrategyFactory = new BasicDeductionStrategyFactory();
    private final RetirementTaxStrategyFactory retirementTaxStrategyFactory = new RetirementTaxStrategyFactory();
    private final EmploymentIncomeDeductionStrategyFactory employmentIncomeDeductionStrategyFactory = new EmploymentIncomeDeductionStrategyFactory();
    private final IncomeTaxRateStrategyFactory incomeTaxRateStrategyFactory = new IncomeTaxRateStrategyFactory();
    private final InhabitantTaxRateStrategyFactory inhabitantTaxRateStrategyFactory = new InhabitantTaxRateStrategyFactory();
    private final ActiveTaxCalculationEngine engine = new ActiveTaxCalculationEngine(
            spouseEngine, dependentEngine, basicDeductionStrategyFactory, retirementTaxStrategyFactory,
            employmentIncomeDeductionStrategyFactory, incomeTaxRateStrategyFactory,
            inhabitantTaxRateStrategyFactory);

    @Test
    void calculate_baseCase_noDeductions() {
        // Given 8,000,000 JPY gross salary
        long grossSalary = 8_000_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2024);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Math:
        // monthly = 666666
        // health = 666666 * 0.05 * 12 = 399999
        // pension = 650000 * 0.0915 * 12 = 713700
        // emp = 666666 * 0.006 * 12 = 47999
        // total social = 1161698

        // Emp income deduction: 1,900,000 -> Emp income = 6,100,000
        // Income tax: (6100000 - 1161699 - 480000) = 4458301 -> round 4458000
        // (4458000 * 0.2) - 427500 = 464100
        // Recon = 464100 * 1.021 = 473846

        // Inhabitant tax: (6100000 - 1161699 - 430000) = 4508301 -> round 4508000
        // 4508000 * 0.1 + 5000 (flat levy) = 455800

        assertThat(result.socialInsurance()).isEqualTo(1_161_699L);
        assertThat(result.incomeTax()).isEqualTo(473_846L);
        assertThat(result.inhabitantTax()).isEqualTo(455_800L);
        assertThat(result.netIncome()).isEqualTo(8_000_000L - 1_161_699L - 473_846L - 455_800L);
    }

    @Test
    void calculate_reiwa8_dynamicBasicDeduction() {
        // Given 8,000,000 JPY gross salary in year 2026 (Reiwa 8)
        // Employment income = 8,000,000 - 1,900,000 = 6,100,000 JPY (within 4.89M ~
        // 6.55M range)
        // Basic deduction for 2026 in this bracket is 670,000 JPY (instead of flat
        // 480,000 JPY)
        long grossSalary = 8_000_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Math:
        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 670,000 (basic
        // deduction)
        // = 4,268,301 -> round down to 1,000 = 4,268,000 JPY
        // baseIncomeTax = (4,268,000 * 0.2) - 427,500 = 426,100 JPY
        // recon = 426,100 * 1.021 = 435,048 JPY

        assertThat(result.socialInsurance()).isEqualTo(1_161_699L);
        assertThat(result.incomeTax()).isEqualTo(435_048L);
        assertThat(result.inhabitantTax()).isEqualTo(455_800L);
        assertThat(result.netIncome()).isEqualTo(8_000_000L - 1_161_699L - 435_048L - 455_800L);
    }

    @Test
    void calculate_reiwa10_dynamicBasicDeduction() {
        // Given 8,000,000 JPY gross salary in year 2028 (Reiwa 10)
        // Employment income = 6,100,000 JPY (within 1.32M ~ 23.5M range)
        // Permanent baseline deduction for 2028+ is 620,000 JPY
        long grossSalary = 8_000_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2028);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Math:
        // taxableIncomeForIncomeTax = 6,100,000 - 1,161,699 (social) - 620,000 (basic
        // deduction)
        // = 4,318,301 -> round down to 1,000 = 4,318,000 JPY
        // baseIncomeTax = (4,318,000 * 0.2) - 427,500 = 436,100 JPY
        // recon = 436,100 * 1.021 = 445,258 JPY

        assertThat(result.socialInsurance()).isEqualTo(1_161_699L);
        assertThat(result.incomeTax()).isEqualTo(445_258L);
        assertThat(result.inhabitantTax()).isEqualTo(455_800L);
        assertThat(result.netIncome()).isEqualTo(8_000_000L - 1_161_699L - 445_258L - 455_800L);
    }

    @Test
    void calculatePensionTax_dynamicBasicDeduction() {
        // Pension income 3,000,000 JPY at age 65
        long pensionIncome = 3_000_000L;
        int age = 65;

        SimulationContext context = new SimulationContext();
        context.setYear(2026); // Reiwa 8: miscellaneousIncome <= 4.89M gets 1,040,000 JPY deduction

        // When
        TaxCalculationResult result = engine.calculatePensionTax(context, pensionIncome, age, null);

        // Math:
        // publicPensionDeduction = max((3,000,000 * 0.25 + 275,000 = 1,025,000),
        // 1,100,000) = 1,100,000
        // miscellaneousIncome = 3,000,000 - 1,100,000 = 1,900,000
        // socialInsurance = 3,000,000 * 0.095 = 285,000
        // basicDeduction (2026, 1.9M <= 4.89M) = 1,040,000
        // totalDeductionsIncomeTax = 285,000 + 1,040,000 = 1,325,000
        // taxableIncomeForIncomeTax = 1,900,000 - 1,325,000 = 575,000
        // baseIncomeTax = 575,000 * 0.05 = 28,750
        // incomeTax = 28,750 + (long)(28,750 * 0.021 = 603.75) = 29,353

        // Inhabitant tax:
        // totalDeductionsInhabitant = 285,000 + 430,000 = 715,000
        // taxableInhabitant = 1,900,000 - 715,000 = 1,185,000
        // inhabitantTax = 1,185,000 * 0.1 + 5,000 (flat levy) = 123,500

        assertThat(result.socialInsurance()).isEqualTo(285_000L);
        assertThat(result.incomeTax()).isEqualTo(29_353L);
        assertThat(result.inhabitantTax()).isEqualTo(123_500L);
    }

    @Test
    void calculate_highIncome26M_dynamicInhabitantTaxDeduction() {
        // Given 26,000,000 JPY gross salary (within 24.0M ~ 24.5M employment income
        // bracket)
        // Employment income = 26,000,000 - 1,950,000 (cap) = 24,050,000 JPY
        // Basic deduction for income tax in 2026: 320,000 JPY
        // Basic deduction for inhabitant tax: 290,000 JPY (reduced from standard
        // 430,000 JPY)
        long grossSalary = 26_000_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Social insurance:
        // monthlySalary = 2,166,666 -> healthBase = 1,390,000, pensionBase = 680,000
        // health = 1,390,000 * 0.05 * 12 = 834,000
        // pension = 680,000 * 0.0915 * 12 = 746,640
        // emp = 26,000,000 * 0.006 = 156,000
        // total social = 1,736,640 JPY

        // Inhabitant tax:
        // employmentIncome = 24,050,000
        // totalDeductionsInhabitant = 1,736,640 + 290,000 (basic deduction) = 2,026,640
        // JPY
        // taxableInhabitant = 24,050,000 - 2,026,640 = 22,023,360 -> round down to
        // 1,000 = 22,023,000 JPY
        // inhabitantTax = 22,023,000 * 0.1 + 5,000 (flat levy) = 2,207,300 JPY

        assertThat(result.socialInsurance()).isEqualTo(1_736_640L);
        assertThat(result.inhabitantTax()).isEqualTo(2_207_300L);
    }

    @Test
    void calculate_highIncome27M_completePhaseOut() {
        // Given 27,000,000 JPY gross salary (employment income > 25.0M JPY)
        // Employment income = 27,000,000 - 1,950,000 = 25,050,000 JPY
        // Basic deduction for income tax in 2026: 0 JPY
        // Basic deduction for inhabitant tax: 0 JPY (complete phase-out)
        long grossSalary = 27_000_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Social insurance:
        // health = 834,000, pension = 746,640, emp = 27,000,000 * 0.006 = 162,000
        // total social = 1,742,640 JPY

        // Inhabitant tax:
        // employmentIncome = 25,050,000
        // totalDeductionsInhabitant = 1,742,640 + 0 (basic deduction) = 1,742,640 JPY
        // taxableInhabitant = 25,050,000 - 1,742,640 = 23,307,360 -> round down to
        // 1,000 = 23,307,000 JPY
        // inhabitantTax = 23,307,000 * 0.1 + 5,000 (flat levy) = 2,335,700 JPY

        assertThat(result.socialInsurance()).isEqualTo(1_742_640L);
        assertThat(result.inhabitantTax()).isEqualTo(2_335_700L);
    }

    @Test
    void calculate_reiwa8_mortgageDeduction_atIncomeCapBoundary_eligible() {
        // Gross salary: 21,950,000 JPY
        // Employment income deduction: 1,950,000 JPY cap
        // Employment income = 21,950,000 - 1,950,000 = 20,000,000 JPY (exactly at the
        // 20M cap)
        // Mortgage deduction: 200,000 JPY with maxTotalIncome = 20,000,000 JPY
        long grossSalary = 21_950_000L;
        long mortgageDeduction = 200_000L;
        long statutoryIncomeCap = 20_000_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // Control result without mortgage deduction
        TaxCalculationResult controlResult = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
                        new ZeroInhabitantTaxLimitStrategy()));

        // Assert: 20,000,000 <= 20,000,000 -> eligible, deduction applied
        // Saving = 200,000 * 1.021 (reconstruction tax) = 204,200 JPY
        assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax() - 204_200L);
        assertThat(result.netIncome()).isEqualTo(controlResult.netIncome() + 204_200L);
    }

    @Test
    void calculate_reiwa8_mortgageDeduction_exceedsCapBy1Yen_disqualified() {
        // Gross salary: 21,950,001 JPY
        // Employment income deduction: 1,950,000 JPY cap
        // Employment income = 21,950,001 - 1,950,000 = 20,000,001 JPY (exceeds 20M cap
        // by 1 JPY)
        // Mortgage deduction: 200,000 JPY with maxTotalIncome = 20,000,000 JPY
        long grossSalary = 21_950_001L;
        long mortgageDeduction = 200_000L;
        long statutoryIncomeCap = 20_000_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // Control result without mortgage deduction
        TaxCalculationResult controlResult = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
                        new ZeroInhabitantTaxLimitStrategy()));

        // Assert: 20,000,001 > 20,000,000 -> disqualified, mortgage deduction forced to
        // 0
        assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
        assertThat(result.netIncome()).isEqualTo(controlResult.netIncome());
    }

    @Test
    void calculate_pre2022_mortgageDeduction_atIncomeCapBoundary_eligible() {
        // Gross salary: 31_950,000 JPY
        // Employment income deduction: 1,950,000 JPY cap
        // Employment income = 31_950,000 - 1,950,000 = 30,000,000 JPY (exactly at the
        // pre-2022 30M cap)
        // Mortgage deduction: 200,000 JPY with maxTotalIncome = 30,000,000 JPY
        long grossSalary = 31_950_000L;
        long mortgageDeduction = 200_000L;
        long statutoryIncomeCap = 30_000_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2021);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // Control result without mortgage deduction
        TaxCalculationResult controlResult = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
                        new ZeroInhabitantTaxLimitStrategy()));

        // Assert: 30,000,000 <= 30,000,000 -> eligible under pre-2022 rules, deduction
        // applied
        assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax() - 204_200L);
        assertThat(result.netIncome()).isEqualTo(controlResult.netIncome() + 204_200L);
    }

    @Test
    void calculate_pre2022_mortgageDeduction_exceedsCapBy1Yen_disqualified() {
        // Gross salary: 31_950_001 JPY
        // Employment income deduction: 1,950,000 JPY cap
        // Employment income = 31_950,001 - 1,950,000 = 30,000,001 JPY (exceeds pre-2022
        // 30M cap by 1 JPY)
        // Mortgage deduction: 200,000 JPY with maxTotalIncome = 30,000,000 JPY
        long grossSalary = 31_950_001L;
        long mortgageDeduction = 200_000L;
        long statutoryIncomeCap = 30_000_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2021);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // Control result without mortgage deduction
        TaxCalculationResult controlResult = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, mortgageDeduction, 0L, statutoryIncomeCap,
                        new ZeroInhabitantTaxLimitStrategy()));

        // Assert: 30,000,001 > 30,000,000 -> disqualified, mortgage deduction forced to
        // 0
        assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
        assertThat(result.netIncome()).isEqualTo(controlResult.netIncome());
    }

    @Test
    void calculate_sameIncome_23M50k_eligibleUnderPre2022_disqualifiedUnderReiwa8() {
        // Gross salary: 25,000,000 JPY
        // Employment income deduction: 1,950,000 JPY cap
        // Employment income = 25,000,000 - 1,950,000 = 23,050,000 JPY
        // 23,050,000 JPY is <= 30,000,000 (pre-2022) but > 20,000,000 (2022 onwards)
        long grossSalary = 25_000_000L;
        long mortgageDeduction = 200_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);

        // Pre-2022 context (statutory cap = 30,000,000 JPY)
        SimulationContext context2021 = new SimulationContext();
        context2021.setProfile(profile);
        context2021.setPensionConfig(pensionConfig);
        context2021.setYear(2021);
        context2021.setHealthInsuranceRate(0.05);
        context2021.setPensionRate(0.0915);
        context2021.setEmploymentInsuranceRate(0.006);

        TaxCalculationResult control2021 = engine.calculate(
                context2021, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));
        TaxCalculationResult result2021 = engine.calculate(
                context2021, grossSalary, 0L, 0,
                0, 0,
                new MortgageYearlyResult(0L, mortgageDeduction, 0L, 30_000_000L, new ZeroInhabitantTaxLimitStrategy()));

        // Under 2021 rules (cap = 30M): 23.05M <= 30M -> eligible!
        assertThat(result2021.incomeTax()).isEqualTo(control2021.incomeTax() - 204_200L);

        // 2026 context (statutory cap = 20,000,000 JPY)
        SimulationContext context2026 = new SimulationContext();
        context2026.setProfile(profile);
        context2026.setPensionConfig(pensionConfig);
        context2026.setYear(2026);
        context2026.setHealthInsuranceRate(0.05);
        context2026.setPensionRate(0.0915);
        context2026.setEmploymentInsuranceRate(0.006);

        TaxCalculationResult control2026 = engine.calculate(
                context2026, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));
        TaxCalculationResult result2026 = engine.calculate(
                context2026, grossSalary, 0L, 0,
                0, 0,
                new MortgageYearlyResult(0L, mortgageDeduction, 0L, 20_000_000L, new ZeroInhabitantTaxLimitStrategy()));

        // Under 2026 rules (cap = 20M): 23.05M > 20M -> disqualified!
        assertThat(result2026.incomeTax()).isEqualTo(control2026.incomeTax());
        assertThat(result2026.netIncome()).isEqualTo(control2026.netIncome());
    }

    @Test
    void calculateRetirementTax_incomeBelowDeduction_returnsZeroTax() {
        SimulationContext context = new SimulationContext();
        context.setYear(2024);

        TaxCalculationResult result = engine.calculateRetirementTax(context, 800_000L, 1);

        assertThat(result.incomeTax()).isZero();
        assertThat(result.inhabitantTax()).isZero();
        assertThat(result.socialInsurance()).isZero();
        assertThat(result.netIncome()).isEqualTo(800_000L);
    }

    @Test
    void calculateRetirementTax_shortTerm_withinThreeMillionExcess_halfTaxation() {
        // 3 years of service -> deduction = max(800,000, 400,000 * 3) = 1,200,000 JPY
        // Retirement income = 3,200,000 JPY -> excess = 2,000,000 JPY (<= 3,000,000)
        // Short-term strategy applies half taxation: taxableIncome = 1,000,000 JPY
        // Income tax rate: 5% (offset 0) -> base = 50,000 JPY -> with 2.1% surtax =
        // 51,050 JPY
        // Inhabitant tax rate: 10% -> 100,000 JPY
        SimulationContext context = new SimulationContext();
        context.setYear(2024);

        TaxCalculationResult result = engine.calculateRetirementTax(context, 3_200_000L, 3);

        assertThat(result.incomeTax()).isEqualTo(51_050L);
        assertThat(result.inhabitantTax()).isEqualTo(100_000L);
        assertThat(result.netIncome()).isEqualTo(3_200_000L - 51_050L - 100_000L);
    }

    @Test
    void calculateRetirementTax_shortTerm_exceedsThreeMillionExcess_antiAbuseLadderApplied() {
        // 3 years of service (<= 5 years) -> deduction = 1,200,000 JPY
        // Retirement income = 10,000,000 JPY -> excess = 8,800,000 JPY (> 3,000,000)
        // Short-term anti-abuse: taxableIncome = 1,500,000 + (8,800,000 - 3,000,000) =
        // 7,300,000 JPY
        // (Without anti-abuse, taxableIncome would have been 8,800,000 / 2 = 4,400,000
        // JPY)
        // Income tax rate: 23% (offset -636,000)
        // baseIncomeTax = 7,300,000 * 0.23 - 636,000 = 1,043,000 JPY
        // incomeTax = 1,043,000 + (long)(1,043,000 * 0.021) = 1,064,903 JPY
        // Inhabitant tax: 7,300,000 * 0.10 = 730,000 JPY
        SimulationContext context = new SimulationContext();
        context.setYear(2024);

        TaxCalculationResult result = engine.calculateRetirementTax(context, 10_000_000L, 3);

        assertThat(result.incomeTax()).isEqualTo(1_064_903L);
        assertThat(result.inhabitantTax()).isEqualTo(730_000L);
        assertThat(result.netIncome()).isEqualTo(10_000_000L - 1_064_903L - 730_000L);
    }

    @Test
    void calculateRetirementTax_standard_overFiveYears_fullHalfTaxationPreserved() {
        // 10 years of service (> 5 years) -> deduction = 4,000,000 JPY
        // Retirement income = 12,800,000 JPY -> excess = 8,800,000 JPY
        // Standard strategy: full half-taxation -> taxableIncome = 8,800,000 / 2 =
        // 4,400,000 JPY
        // Income tax rate: 20% (offset -427,500)
        // baseIncomeTax = 4,400,000 * 0.20 - 427,500 = 452,500 JPY
        // incomeTax = 452,500 + (long)(452,500 * 0.021) = 462,002 JPY
        // Inhabitant tax: 4,400,000 * 0.10 = 440,000 JPY
        SimulationContext context = new SimulationContext();
        context.setYear(2024);

        TaxCalculationResult result = engine.calculateRetirementTax(context, 12_800_000L, 10);

        assertThat(result.incomeTax()).isEqualTo(462_002L);
        assertThat(result.inhabitantTax()).isEqualTo(440_000L);
        assertThat(result.netIncome()).isEqualTo(12_800_000L - 462_002L - 440_000L);
    }

    @Test
    void calculate_crossEraEmploymentIncomeDeduction_provesBugFixFor2028() {
        // Gross salary: 1,800,000 JPY
        // Statutory Minimum Deduction across 4 Eras:
        // - 2024 (Legacy): 1.8M * 40% - 100k = 620,000 JPY -> Employment Income =
        // 1,180,000 JPY
        // - 2025 (Reiwa 7): 650,000 JPY (Minimum Guarantee) -> Employment Income =
        // 1,150,000 JPY
        // - 2026 (Reiwa 8): 740,000 JPY (Special Sunset) -> Employment Income =
        // 1,060,000 JPY
        // - 2028 (Reiwa 10): 690,000 JPY (Statutory Baseline) -> Employment Income =
        // 1,110,000 JPY
        // (Bug in Issue 142 caused 2028+ to wrongly remain at 740,000 JPY,
        // underestimating income by 50,000 JPY)
        // 1.8M JPY is an optimal boundary test value exceeding the 1.68M/1.78M
        // exemption thresholds,
        // ensuring non-zero income and inhabitant taxes across all 4 eras without loose
        // assertions.
        long grossSalary = 1_800_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);

        // 1. Year 2024 (Legacy): 620,000 JPY deduction
        SimulationContext context2024 = new SimulationContext();
        context2024.setProfile(profile);
        context2024.setPensionConfig(pensionConfig);
        context2024.setYear(2024);
        context2024.setHealthInsuranceRate(0.0);
        context2024.setPensionRate(0.0);
        context2024.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2024 = engine.calculate(
                context2024, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
        // Employment income = 1,800,000 - 620,000 = 1,180,000
        // Basic deduction 2024: 480,000 (income tax), 430,000 (inhabitant tax)
        // Taxable income tax = 1,180,000 - 480,000 = 700,000 -> 700,000 * 5% = 35,000
        // -> recon: 35,735
        // Taxable inhab tax = 1,180,000 - 430,000 = 750,000 -> 750,000 * 10% + 5,000
        // (flat levy) = 80,000
        assertThat(res2024.incomeTax()).isEqualTo(35_735L);
        assertThat(res2024.inhabitantTax()).isEqualTo(80_000L);

        // 2. Year 2025 (Reiwa 7): Minimum Guarantee 650,000 JPY
        SimulationContext context2025 = new SimulationContext();
        context2025.setProfile(profile);
        context2025.setPensionConfig(pensionConfig);
        context2025.setYear(2025);
        context2025.setHealthInsuranceRate(0.0);
        context2025.setPensionRate(0.0);
        context2025.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2025 = engine.calculate(
                context2025, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
        // Employment income = 1,800,000 - 650,000 = 1,150,000
        // Basic deduction 2025 (Reiwa 7, income <= 1.32M): 950,000 (income tax)
        // Taxable income tax = 1,150,000 - 950,000 = 200,000 -> 200,000 * 5% = 10,000
        // -> recon: 10,210
        // Taxable inhab tax = 1,150,000 - 430,000 = 720,000 -> 720,000 * 10% + 5,000
        // (flat levy) = 77,000
        assertThat(res2025.incomeTax()).isEqualTo(10_210L);
        assertThat(res2025.inhabitantTax()).isEqualTo(77_000L);

        // 3. Year 2026 (Reiwa 8): Special Sunset Minimum 740,000 JPY
        SimulationContext context2026 = new SimulationContext();
        context2026.setProfile(profile);
        context2026.setPensionConfig(pensionConfig);
        context2026.setYear(2026);
        context2026.setHealthInsuranceRate(0.0);
        context2026.setPensionRate(0.0);
        context2026.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2026 = engine.calculate(
                context2026, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
        // Employment income = 1,800,000 - 740,000 = 1,060,000
        // Basic deduction 2026 (Reiwa 8, income <= 4.89M): 1,040,000 (income tax)
        // Taxable income tax = 1,060,000 - 1,040,000 = 20,000 -> 20,000 * 5% = 1,000 ->
        // recon: 1,021
        // Taxable inhab tax = 1,060,000 - 430,000 = 630,000 -> 630,000 * 10% + 5,000
        // (flat levy) = 68,000
        assertThat(res2026.incomeTax()).isEqualTo(1_021L);
        assertThat(res2026.inhabitantTax()).isEqualTo(68_000L);

        // 4. Year 2028 (Reiwa 10): Statutory Baseline Reversion 690,000 JPY
        SimulationContext context2028 = new SimulationContext();
        context2028.setProfile(profile);
        context2028.setPensionConfig(pensionConfig);
        context2028.setYear(2028);
        context2028.setHealthInsuranceRate(0.0);
        context2028.setPensionRate(0.0);
        context2028.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2028 = engine.calculate(
                context2028, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));
        // Employment income = 1,800,000 - 690,000 = 1,110,000 (50k higher than 2026!)
        // Basic deduction 2028 (Reiwa 10, income <= 1.32M): 990,000 (income tax)
        // Taxable income tax = 1,110,000 - 990,000 = 120,000 -> 120,000 * 5% = 6,000 ->
        // recon: 6,126 (5,105 higher than 2026!)
        // Taxable inhab tax = 1,110,000 - 430,000 = 680,000 -> 680,000 * 10% + 5,000
        // (flat levy) = 73,000 (5,000 higher than 2026!)
        assertThat(res2028.incomeTax()).isEqualTo(6_126L);
        assertThat(res2028.inhabitantTax()).isEqualTo(73_000L);
    }

    @Test
    void calculate_crossEraIncomeTaxRate_provesStrategyPattern() {
        // High income earner with 60M gross salary to test top bracket differentiation
        long grossSalary = 60_000_000L;
        UserProfile profile = new UserProfile("test", null, null, null);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);

        // Context 2014: Pre-2015 era with 40% cap
        SimulationContext context2014 = new SimulationContext();
        context2014.setProfile(profile);
        context2014.setPensionConfig(pensionConfig);
        context2014.setYear(2014);
        context2014.setHealthInsuranceRate(0.0);
        context2014.setPensionRate(0.0);
        context2014.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2014 = engine.calculate(
                context2014, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Context 2015: Standard 2015+ era with 45% top bracket
        SimulationContext context2015 = new SimulationContext();
        context2015.setProfile(profile);
        context2015.setPensionConfig(pensionConfig);
        context2015.setYear(2015);
        context2015.setHealthInsuranceRate(0.0);
        context2015.setPensionRate(0.0);
        context2015.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2015 = engine.calculate(
                context2015, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Income tax for 2015 should be strictly higher than 2014 due to the 45%
        // statutory bracket
        assertThat(res2015.incomeTax()).isGreaterThan(res2014.incomeTax());
    }

    @Test
    void calculate_crossEraInhabitantTaxRate_provesStrategyPattern() {
        // Low-to-medium income earner with 3,000,000 gross salary
        long grossSalary = 3_000_000L;
        UserProfile profile = new UserProfile("test", null, null, null);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);

        // Context 2006: Pre-2007 Trinity reform era (tiered 5% ~ 13%)
        SimulationContext context2006 = new SimulationContext();
        context2006.setProfile(profile);
        context2006.setPensionConfig(pensionConfig);
        context2006.setYear(2006);
        context2006.setHealthInsuranceRate(0.0);
        context2006.setPensionRate(0.0);
        context2006.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2006 = engine.calculate(
                context2006, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Context 2007: Post-reform flat 10% era
        SimulationContext context2007 = new SimulationContext();
        context2007.setProfile(profile);
        context2007.setPensionConfig(pensionConfig);
        context2007.setYear(2007);
        context2007.setHealthInsuranceRate(0.0);
        context2007.setPensionRate(0.0);
        context2007.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult res2007 = engine.calculate(
                context2007, grossSalary, 0L, 0, 0, 0,
                new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Under 2006 tiers, taxable income around 1.5M was taxed at 5%, whereas in 2007
        // it was taxed at flat 10%
        assertThat(res2007.inhabitantTax()).isGreaterThan(res2006.inhabitantTax());
    }

    @Test
    void calculate_firstYearMortgage_exceedsCap_recordRefundAndInhabitantCreditAreZero() {
        // Issue 139: Gross salary 25,000,000 JPY (employment income > 20,000,000 JPY)
        // in 2026
        // Mortgage in occupancy year: kakuteiDeduction = 200,000 JPY, yearEndDeduction
        // = 0L
        long grossSalary = 25_000_000L;
        long kakuteiDeduction = 200_000L;
        long statutoryIncomeCap = 20_000_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 680_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // When
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap,
                        new Post2022InhabitantTaxLimitStrategy()));

        // Assert: high income earner is disqualified in first year; packaged record
        // must be 0 for refund and inhabitant rollover
        assertThat(result).isNotNull();
        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
        assertThat(record).isNotNull();
        assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(0L);
        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
    }

    @Test
    void calculate_firstYearMortgage_underCap_withholdingPreserved_kakuteiRefundRecorded() {
        // Gross salary 8,000,000 JPY in 2026 (below 20M cap)
        // First year mortgage: yearEndDeduction = 0L, kakuteiDeduction = 200,000 JPY
        long grossSalary = 8_000_000L;
        long kakuteiDeduction = 200_000L;
        long statutoryIncomeCap = 20_000_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        // Control result without mortgage deduction
        TaxCalculationResult controlResult = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, statutoryIncomeCap, new ZeroInhabitantTaxLimitStrategy()));

        // First year mortgage run
        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap,
                        new Post2022InhabitantTaxLimitStrategy()));

        // In first year, withholding tax is preserved (current year income tax is NOT
        // reduced)
        assertThat(result.incomeTax()).isEqualTo(controlResult.incomeTax());
        assertThat(result.netIncome()).isEqualTo(controlResult.netIncome());

        // PreviousYearTaxRecord packages the Kakutei Shinkoku refund capped by base
        // income tax
        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
        assertThat(record).isNotNull();
        // Since baseIncomeTax (426,100) > kakuteiDeduction (200,000), refund is full
        // 200,000
        assertThat(record.pendingKakuteiShinkokuRefund()).isEqualTo(200_000L);
        // All kakutei deduction was absorbed by income tax, so no leftover for
        // inhabitant tax
        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isEqualTo(0L);
    }

    @Test
    void calculate_firstYearMortgage_lowIncome_cappedAtBaseIncomeTax_andRolloverToInhabitantTax() {
        // Low income where baseIncomeTax < kakuteiDeduction
        // Gross salary 3,000,000 JPY in 2026
        // kakuteiDeduction = 200,000 JPY
        long grossSalary = 3_000_000L;
        long kakuteiDeduction = 200_000L;
        long statutoryIncomeCap = 20_000_000L;

        UserProfile profile = new UserProfile("test", null, null, null);
        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2026);
        context.setHealthInsuranceRate(0.05);
        context.setPensionRate(0.0915);
        context.setEmploymentInsuranceRate(0.006);

        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, kakuteiDeduction, statutoryIncomeCap,
                        new Post2022InhabitantTaxLimitStrategy()));

        assertThat(result).isNotNull();
        assertThat(result.incomeTax()).isGreaterThan(0L);

        PreviousYearTaxRecord record = context.getPreviousYearTaxRecord();
        assertThat(record).isNotNull();
        // Refund must be capped at baseIncomeTax
        assertThat(record.pendingKakuteiShinkokuRefund()).isGreaterThan(0L);
        assertThat(record.pendingKakuteiShinkokuRefund()).isLessThan(kakuteiDeduction);

        // Remaining deduction rolls over to inhabitant tax, capped at statutory
        // inhabitant tax limit (<= 97,500)
        long remaining = kakuteiDeduction - record.pendingKakuteiShinkokuRefund();
        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isGreaterThan(0L);
        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isLessThanOrEqualTo(remaining);
        assertThat(record.unusedMortgageDeductionForInhabitantTax()).isLessThanOrEqualTo(97_500L);
    }

    @Test
    void calculate_lowIncome_belowFlatLevyThreshold_exemptFromInhabitantTax() {
        // Gross salary: 900,000 JPY
        // Employment income deduction: 550,000 JPY (minimum deduction in 2024)
        // Employment income = 350,000 JPY (<= 450,000 JPY statutory threshold under
        // Local Tax Act Art. 295(1)(2))
        long grossSalary = 900_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2024);
        context.setHealthInsuranceRate(0.0);
        context.setPensionRate(0.0);
        context.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Both income levy and per-capita flat levy are 0 JPY
        assertThat(result.inhabitantTax()).isZero();
    }

    @Test
    void calculate_incomeAboveFlatLevyThreshold_zeroTaxableIncome_paysOnlyFlatLevy() {
        // Gross salary: 1,050,000 JPY
        // Employment income deduction in 2024: 550,000 JPY -> Employment income =
        // 500,000 JPY (> 450,000 JPY threshold)
        // Health insurance at 10% -> approx 105,600 JPY
        // Total inhabitant deductions = 105,600 + 430,000 (basic deduction) = 535,600
        // JPY > 500,000 JPY
        // Taxable income for inhabitant tax = 0 JPY
        // But total income (500,000 JPY) > 450,000 JPY threshold -> Flat levy 5,000 JPY
        // applies!
        long grossSalary = 1_050_000L;
        UserProfile profile = new UserProfile("test", null, null, null);

        SimulationContext context = new SimulationContext();
        context.setProfile(profile);
        PensionConfigDto pensionConfig = new PensionConfigDto(null, null, null, 650_000L, 1_500_000L);
        context.setPensionConfig(pensionConfig);
        context.setYear(2024);
        context.setHealthInsuranceRate(0.10);
        context.setPensionRate(0.0);
        context.setEmploymentInsuranceRate(0.0);

        TaxCalculationResult result = engine.calculate(
                context, grossSalary, 0L, 0,
                0, 0, new MortgageYearlyResult(0L, 0L, 0L, 0L, new ZeroInhabitantTaxLimitStrategy()));

        // Inhabitant tax is purely the 5,000 JPY per-capita flat levy
        assertThat(result.inhabitantTax()).isEqualTo(5_000L);
    }

    @Test
    void calculateRetirementTax_highRetirementIncome_exemptFromFlatLevy() {
        // High retirement income: 20,000,000 JPY, 10 years of service
        // Under Local Tax Act Art. 50-2 & 328, retirement income is separate taxation
        // exempt from flat levy.
        // Deduction: 4,000,000 JPY -> excess = 16,000,000 JPY -> taxable = 8,000,000
        // JPY
        // Inhabitant tax: 8,000,000 * 10% = 800,000 JPY (strictly 0 JPY flat levy)
        SimulationContext context = new SimulationContext();
        context.setYear(2024);

        TaxCalculationResult result = engine.calculateRetirementTax(context, 20_000_000L, 10);

        assertThat(result.inhabitantTax()).isEqualTo(800_000L);
    }
}
