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
import java.time.Period;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.education.ChildEducationSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.currency.CurrencyConversionService;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.IncomeExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.MortgageCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationAbsoluteTimeline;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationExpenseCalculator;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationProfileResolver;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.EducationTimelineProjector;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.education.ResolvedEducationProfile;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.InsurancePolicyDto;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.pension.PensionConfigDto;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor(onConstructor_ = @Autowired)
public class SimulationContextFactory {

    private final FinanceHubConfigService configService;
    private final PensionRuleConfigService pensionRuleConfigService;
    private final UserProfileRepository userProfileRepository;
    private final ChildProfileRepository childProfileRepository;
    private final IncomeExpenseCalculator incomeExpenseCalculator;

    private final CurrencyConversionService currencyConversionService;
    private final ETradeAccountSnapshotRepository etradeRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final MortgageRecordRepository mortgageRecordRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final MortgageCalculator mortgageSimulationService;

    private final SalaryContextBuilder salaryContextBuilder;
    private final NisaContextBuilder nisaContextBuilder;
    private final PensionContextBuilder pensionContextBuilder;
    private final DefinedContributionContextBuilder dcContextBuilder;

    private final EducationConfigService educationConfigService;
    private final ChildEducationSettingsRepository childEducationSettingsRepository;
    private final EducationProfileResolver educationProfileResolver;
    private final EducationTimelineProjector educationTimelineProjector;
    private final EducationExpenseCalculator educationExpenseCalculator;

    public SimulationContext createContext() {
        SimulationContext context = new SimulationContext();
        String activeConfigName = configService.getAllConfigs().getEnabledConfigName();
        FinanceHubConfig config = configService.getConfig(activeConfigName);
        context.setConfig(config);

        String activePensionConfigName = pensionRuleConfigService.getAllConfigs().getEnabledConfigName();
        PensionRuleConfigResult pensionConfigResult = pensionRuleConfigService.getConfig(activePensionConfigName);
        PensionConfigDto pensionConfigDto = new PensionConfigDto(
                pensionConfigResult.getMaxBasicPensionAmount(),
                pensionConfigResult.getMaxBasicPensionMonths(),
                pensionConfigResult.getKouseiMultiplierPerMille(),
                pensionConfigResult.getKouseiSalaryCapMonthly(),
                pensionConfigResult.getKouseiBonusCapPerPayment());
        context.setPensionConfig(pensionConfigDto);

        String userId = SecurityUtil.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(Objects.requireNonNull(userId)).orElse(null);
        context.setProfile(profile);

        List<ChildProfile> children = childProfileRepository.findAllByOwnerUserId(userId);
        context.setChildren(children);

        int currentAge = 30;
        int currentYear = YearMonth.now().getYear();
        if (profile != null && profile.getBirthDate() != null) {
            currentAge = Period.between(profile.getBirthDate(), LocalDate.now()).getYears();
        }
        context.setCurrentAge(currentAge);
        context.setCurrentYear(currentYear);

        salaryContextBuilder.initializeSalaryContext(context, config, currentYear, userId);

        long baselineMonthlyExpenses = incomeExpenseCalculator.calculateDynamicBaselineMonthlyExpenses(config, profile);
        context.setBaselineMonthlyExpenses(baselineMonthlyExpenses);

        pensionContextBuilder.initializePensionContext(context, userId);

        nisaContextBuilder.initializeNisaContext(context, config, userId);

        dcContextBuilder.initializeDefinedContributionContext(context, config, userId);

        // Currency conversion for company stock:
        CurrencyCode targetCurrency = currencyConversionService.resolveTargetCurrency(profile, config);

        long companyStockBalance = etradeRepository.findFirstByOwnerUserIdOrderByRecordDateDesc(userId)
                .map(snapshot -> currencyConversionService.convertToTargetCurrency(
                        snapshot.getCurrentAccountValue(), targetCurrency, snapshot.getExchangeRate()))
                .orElse(0L);
        context.setCompanyStockBalance(companyStockBalance);

        long initialLiquidCash = 0L;
        List<String> bankNames = bankTransactionRepository.findDistinctBankNamesByOwnerUserId(userId);
        for (String bankName : bankNames) {
            BankTransaction latestTx = bankTransactionRepository
                    .findFirstByOwnerUserIdAndBankNameOrderByTransactionDateDesc(userId, bankName);
            if (latestTx != null && latestTx.getBalance() != null) {
                initialLiquidCash += latestTx.getBalance();
            }
        }
        context.setCurrentLiquidCash(initialLiquidCash);

        List<MortgageRecord> allMortgages = mortgageRecordRepository.findAllByOwnerUserId(userId);
        mortgageSimulationService.initializeMortgages(context, allMortgages);

        List<InsurancePolicy> policies = insurancePolicyRepository.findAllByOwnerUserId(userId);
        List<InsurancePolicyDto> policyDtos = policies.stream()
                .map(p -> new InsurancePolicyDto(p.getPolicyType(), p.getAnnualPremium(), p.getEndDate()))
                .collect(Collectors.toList());
        context.setInsurancePolicies(policyDtos);

        // Precompute education expenses cache using 3-step pipeline
        Map<Long, Map<Integer, Long>> educationCache = new HashMap<>();
        if (children != null && !children.isEmpty() && educationConfigService != null
                && educationProfileResolver != null && educationTimelineProjector != null
                && educationExpenseCalculator != null) {
            EducationConfig eduConfig = educationConfigService.getDefaultDomainConfig();
            for (ChildProfile child : children) {
                if (child == null || child.getId() == null) {
                    continue;
                }
                if (child.getBirthDate() == null) {
                    log.warn("Child profile id={} has no birthDate. Skipping education cost calculation.",
                            child.getId());
                    continue;
                }
                ChildEducationSettings settings = childEducationSettingsRepository != null
                        ? childEducationSettingsRepository.findByChildProfileId(child.getId()).orElse(null)
                        : null;
                if (settings == null) {
                    settings = new ChildEducationSettings(child.getId());
                }
                ResolvedEducationProfile resolvedProfile = educationProfileResolver.resolve(settings, eduConfig);
                EducationAbsoluteTimeline timeline = educationTimelineProjector.project(child.getBirthDate(),
                        resolvedProfile);
                Map<Integer, Long> costMap = educationExpenseCalculator.buildAnnualCostMap(timeline, currentYear,
                        YearMonth.now().getMonthValue());
                educationCache.put(child.getId(), costMap);
            }
        }
        context.setChildEducationCostCache(educationCache);

        return context;
    }
}
