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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.factory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SalaryContextBuilder {

    private final SalaryRecordRepository salaryRecordRepository;
    private final SalaryUserSettingsRepository salaryUserSettingsRepository;

    public record BonusInfo(long annualBonus, int bonusPaymentCount) {
    }

    public BonusInfo resolveAnnualBonusInfo(List<SalaryRecord> salaryRecords, String userId) {
        return resolveAnnualBonusInfo(salaryRecords, salaryUserSettingsRepository.findByOwnerUserId(userId));
    }

    private BonusInfo resolveAnnualBonusInfo(List<SalaryRecord> salaryRecords,
            Optional<SalaryUserSettings> salarySettingsOpt) {
        long currentAnnualBonus = 0L;
        int currentBonusPaymentCount = 0;

        LocalDate oneYearAgo = LocalDate.now().minusMonths(12);

        for (SalaryRecord sr : salaryRecords) {
            if (sr.getPaymentDate() == null || sr.getPaymentDate().isBefore(oneYearAgo)) {
                break;
            }
            if (sr.getType() == SalaryType.BONUS) {
                Long grossObj = sr.getGrossAmount();
                long gross = grossObj != null ? grossObj : 0L;
                currentAnnualBonus += gross;
                currentBonusPaymentCount++;
            }
        }

        if (salarySettingsOpt.isPresent()) {
            SalaryUserSettings salarySettings = salarySettingsOpt.get();
            Boolean useHist = salarySettings.getUseHistoricalBonusForProjection();
            if (useHist == null || !useHist) {
                Long expected = salarySettings.getExpectedAnnualBonus();
                currentAnnualBonus = expected != null ? expected : 0L;
                currentBonusPaymentCount = currentAnnualBonus > 0 ? 2 : 0; // Assume 2 payments if there is a bonus
            }
        }

        return new BonusInfo(currentAnnualBonus, currentBonusPaymentCount);
    }

    public void initializeSalaryContext(SimulationContext context, FinanceHubConfig config, int currentYear,
            String userId) {
        long baseMonthlySalary;
        List<SalaryRecord> salaryRecords = salaryRecordRepository.findAllByOwnerUserIdOrderByPaymentDateDesc(userId);
        Optional<SalaryUserSettings> salarySettingsOpt = salaryUserSettingsRepository.findByOwnerUserId(userId);

        BonusInfo bonusInfo = resolveAnnualBonusInfo(salaryRecords, salarySettingsOpt);
        double salaryGrowthRate = salarySettingsOpt.map(s -> s.getSalaryGrowthRate()).orElse(0.0);
        long currentAnnualBonus = bonusInfo.annualBonus();
        int currentBonusPaymentCount = bonusInfo.bonusPaymentCount();
        double healthInsuranceRate = TaxConstants.DEFAULT_HEALTH_INSURANCE_RATE; // Default fallback (~4.9%)
        double pensionRate = TaxConstants.DEFAULT_PENSION_RATE; // Default fallback (~9.15%)
        double employmentInsuranceRate = TaxConstants.DEFAULT_EMPLOYMENT_INSURANCE_RATE; // Default fallback (~0.6%)

        LocalDate oneYearAgo = LocalDate.now().minusMonths(12);

        long totalMonthlyIncome = 0L;
        int monthlyRecordCount = 0;

        SalaryRecord latestMonthly = null;

        long ytdGross = 0L;
        long ytdBaseSalary = 0L;
        long ytdNetAmount = 0L;
        long ytdIncomeTax = 0L;
        long ytdResidentTax = 0L;
        long ytdHealthInsurance = 0L;
        long ytdPension = 0L;
        long ytdEmploymentInsurance = 0L;

        for (SalaryRecord sr : salaryRecords) {
            if (sr.getPaymentDate() != null && sr.getPaymentDate().getYear() == currentYear) {
                Long grossAmount = sr.getGrossAmount();
                ytdGross += (grossAmount != null ? grossAmount : 0L);

                Long netAmount = sr.getNetAmount();
                ytdNetAmount += (netAmount != null ? netAmount : 0L);

                Long incomeTax = sr.getIncomeTax();
                ytdIncomeTax += (incomeTax != null ? incomeTax : 0L);

                Long residentTax = sr.getResidentTax();
                ytdResidentTax += (residentTax != null ? residentTax : 0L);

                Long healthInsurance = sr.getHealthInsurance();
                ytdHealthInsurance += (healthInsurance != null ? healthInsurance : 0L);

                Long pension = sr.getPension();
                ytdPension += (pension != null ? pension : 0L);

                Long employmentInsurance = sr.getEmploymentInsurance();
                ytdEmploymentInsurance += (employmentInsurance != null ? employmentInsurance : 0L);

                if (sr.getType() == SalaryType.MONTHLY_SALARY) {
                    Long baseSalary = sr.getBaseSalary();
                    ytdBaseSalary += (baseSalary != null ? baseSalary : 0L);
                }

                if (sr.getPaymentDate().getMonthValue() == YearMonth.now().getMonthValue()) {
                    context.setCurrentMonthSalaryRecorded(true);
                }
            }

            if (sr.getPaymentDate() == null || sr.getPaymentDate().isBefore(oneYearAgo)) {
                if (sr.getPaymentDate() != null && sr.getPaymentDate().getYear() < currentYear) {
                    break;
                }
                continue;
            }

            if (sr.getType() == SalaryType.MONTHLY_SALARY) {
                Long baseObj = sr.getBaseSalary();
                long base = baseObj != null ? baseObj : 0L;
                totalMonthlyIncome += base; // Exclude commission for future projection to ensure perfect consistency
                monthlyRecordCount++;

                if (latestMonthly == null) {
                    latestMonthly = sr; // Due to DESC sort, the first one found is the latest
                }
            }
        }

        if (monthlyRecordCount > 0) {
            baseMonthlySalary = totalMonthlyIncome / monthlyRecordCount;

            if (latestMonthly != null) {
                Long latestGrossObj = latestMonthly.getGrossAmount();
                long latestGross = latestGrossObj != null ? latestGrossObj : 0L;
                if (latestGross > 0) {
                    long healthCap = TaxConstants.HEALTH_INSURANCE_MONTHLY_CAP;
                    long pensionCap = context.getPensionConfig().getKouseiSalaryCapMonthly();

                    long healthBase = Math.min(latestGross, healthCap);
                    long pensionBase = Math.min(latestGross, pensionCap);

                    if (latestMonthly.getHealthInsurance() != null && healthBase > 0) {
                        healthInsuranceRate = (double) latestMonthly.getHealthInsurance() / healthBase;
                    }
                    if (latestMonthly.getPension() != null && pensionBase > 0) {
                        pensionRate = (double) latestMonthly.getPension() / pensionBase;
                    }
                    if (latestMonthly.getEmploymentInsurance() != null) {
                        employmentInsuranceRate = (double) latestMonthly.getEmploymentInsurance() / latestGross;
                    }
                }
            }
        } else {
            Long expectedObj = config.getPensionExpectedMonthlySalary();
            baseMonthlySalary = expectedObj != null ? expectedObj : 0L;
        }

        context.setBaseMonthlySalary(baseMonthlySalary);
        context.setCurrentAnnualSalary(baseMonthlySalary * 12);
        context.setCurrentAnnualBonus(currentAnnualBonus);
        context.setCurrentBonusPaymentCount(currentBonusPaymentCount);
        context.setSalaryGrowthRate(salaryGrowthRate);
        context.setHealthInsuranceRate(healthInsuranceRate);
        context.setPensionRate(pensionRate);
        context.setEmploymentInsuranceRate(employmentInsuranceRate);
        context.setYtdGrossIncome(ytdGross);
        context.setYtdBaseSalary(ytdBaseSalary);
        context.setYtdNetAmount(ytdNetAmount);
        context.setYtdIncomeTax(ytdIncomeTax);
        context.setYtdResidentTax(ytdResidentTax);
        context.setYtdHealthInsurance(ytdHealthInsurance);
        context.setYtdPension(ytdPension);
        context.setYtdEmploymentInsurance(ytdEmploymentInsurance);
    }
}
