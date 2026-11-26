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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.pension.PensionRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.profile.user.UserProfileService;
import ch.lin.finance.hub.backend.api.app.service.profile.user.result.UserProfileResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.PensionCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SalaryContextBuilder;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.SimulationParameterResolver;
import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link PensionProjectionService}.
 */
@Service
@RequiredArgsConstructor
public class PensionProjectionServiceImpl implements PensionProjectionService {

    private final PensionRecordRepository pensionRecordRepository;
    private final FinanceHubConfigService configService;
    private final PensionRuleConfigService pensionRuleConfigService;
    private final SalaryRecordRepository salaryRecordRepository;
    private final PensionCalculator pensionCalculator;
    private final UserProfileService userProfileService;
    private final SalaryContextBuilder salaryContextBuilder;

    @Override
    @Transactional(readOnly = true)
    public List<PensionProjectionYearlyResult> calculateProjection(String userId, int maxAdditionalYears,
            Integer pensionStartAgeOverride) {
        String activePensionConfigName = pensionRuleConfigService.getAllConfigs().getEnabledConfigName();
        PensionRuleConfigResult pensionRuleConfig = pensionRuleConfigService.getConfig(activePensionConfigName);
        PensionConfigDto pensionConfigDto = new PensionConfigDto(
                pensionRuleConfig.getMaxBasicPensionAmount(),
                pensionRuleConfig.getMaxBasicPensionMonths(),
                pensionRuleConfig.getKouseiMultiplierPerMille(),
                pensionRuleConfig.getKouseiSalaryCapMonthly(),
                pensionRuleConfig.getKouseiBonusCapPerPayment());

        String activeConfigName = configService.getAllConfigs().getEnabledConfigName();
        FinanceHubConfig config = configService.getConfig(activeConfigName);

        Optional<SalaryRecord> latestMonthlySalary = salaryRecordRepository
                .findFirstByTypeOrderByPaymentDateDesc(SalaryType.MONTHLY_SALARY);
        long expectedSalary;
        if (latestMonthlySalary.isPresent() && latestMonthlySalary.get().getGrossAmount() != null) {
            expectedSalary = latestMonthlySalary.get().getGrossAmount();
        } else {
            Long expectedSalaryObj = config.getPensionExpectedMonthlySalary();
            expectedSalary = expectedSalaryObj != null ? expectedSalaryObj : 0L;
        }

        UserProfileResult userProfile = userProfileService.getUserProfile(Objects.requireNonNull(userId))
                .orElse(null);
        int lifeExpectancy = SimulationParameterResolver.resolveLifeExpectancy(
                (userProfile != null) ? userProfile.getLifeExpectancy() : null,
                config.getLifeExpectancy(), null);

        int pensionStartAge = SimulationParameterResolver.resolvePensionStartAge(
                (userProfile != null) ? userProfile.getPensionStartAge() : null,
                config.getPensionStartAge(), pensionStartAgeOverride);

        List<PensionRecord> records = pensionRecordRepository
                .findAll(Sort.by(Sort.Direction.DESC, "recordDate"));
        PensionRecord latestRecord = records.isEmpty() ? null : records.get(0);

        long currentKouseiAmount = 0L;
        int currentKokuminMonths = 0;
        int currentKouseiMonths = 0;

        if (latestRecord != null) {
            Long kouseiAmtObj = latestRecord.getKouseiPensionAmount();
            currentKouseiAmount = kouseiAmtObj != null ? kouseiAmtObj : 0L;

            Integer kokuminMonthsObj = latestRecord.getKokuminTotalMonths();
            currentKokuminMonths = kokuminMonthsObj != null ? kokuminMonthsObj : 0;

            Integer kouseiMonthsObj = latestRecord.getKouseiTotalMonths();
            currentKouseiMonths = kouseiMonthsObj != null ? kouseiMonthsObj : 0;
        }

        List<SalaryRecord> salaryRecords = salaryRecordRepository.findAllByOrderByPaymentDateDesc();
        SalaryContextBuilder.BonusInfo bonusInfo = salaryContextBuilder.resolveAnnualBonusInfo(salaryRecords);
        long annualBaseSalary = expectedSalary * 12;
        long annualBonus = bonusInfo.annualBonus();
        int bonusPaymentCount = bonusInfo.bonusPaymentCount();

        List<PensionProjectionYearlyResult> projections = new ArrayList<>();

        for (int i = 0; i <= maxAdditionalYears; i++) {
            int additionalMonths = i * 12;

            // Basic Pension Projection
            long projectedBasic = pensionCalculator.calculateProjectedBasicPension(pensionConfigDto,
                    currentKokuminMonths, currentKouseiMonths, additionalMonths, pensionStartAge);

            // Kousei Pension Projection
            long kouseiIncrement = i * pensionCalculator.calculateYearlyKouseiBenefitIncrement(pensionConfigDto,
                    annualBaseSalary, annualBonus, bonusPaymentCount);
            long baseAmount = currentKouseiAmount + kouseiIncrement;
            long projectedKousei = Math
                    .round(baseAmount * pensionCalculator.calculatePensionMultiplier(pensionStartAge));

            long totalAnnual = projectedBasic + projectedKousei;
            int yearsInRetirement = Math.max(0, lifeExpectancy - pensionStartAge);
            long lifetimeTotal = totalAnnual * yearsInRetirement;

            PensionProjectionYearlyResult result = new PensionProjectionYearlyResult(i, projectedBasic, projectedKousei,
                    totalAnnual, lifetimeTotal);
            result.setOwnerUserId(userId);
            result.setPrivacyLevel(latestRecord != null ? latestRecord.getPrivacyLevel() : PrivacyLevel.PRIVATE);
            projections.add(result);
        }

        return projections;
    }
}
