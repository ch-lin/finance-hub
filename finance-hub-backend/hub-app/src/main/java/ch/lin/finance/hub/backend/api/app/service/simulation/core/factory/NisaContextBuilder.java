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

import java.util.ArrayList;
import java.util.Objects;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaContributionSettingRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.NisaAccountType;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NisaContextBuilder {

    private final NisaSnapshotRecordRepository nisaRepository;
    private final NisaContributionSettingRepository nisaContributionSettingRepository;
    private final NisaUserSettingsRepository nisaUserSettingsRepository;

    public void initializeNisaContext(SimulationContext context, FinanceHubConfig config) {
        long startingNisaBalance = 0L;
        long startingNisaPrincipal = 0L;
        long startingGrowthNisaPrincipal = 0L;
        var latestNisaOpt = nisaRepository.findFirstByOrderByRecordDateDesc();
        var nisaRecords = new ArrayList<NisaSnapshotRecord>();
        var nisaUserSettingsOpt = nisaUserSettingsRepository.findFirstBy();

        if (latestNisaOpt.isPresent()) {
            nisaRecords.addAll(nisaRepository.findByRecordDate(latestNisaOpt.get().getRecordDate()));
            startingNisaBalance = nisaRecords.stream().mapToLong(record -> {
                Long val = record.getCurrentMarketValue();
                return val != null ? val : 0L;
            }).sum();
            startingNisaPrincipal = nisaRecords.stream().mapToLong(record -> {
                Long val = record.getTotalPrincipal();
                return val != null ? val : 0L;
            }).sum();
            startingGrowthNisaPrincipal = nisaRecords.stream()
                    .filter(record -> NisaAccountType.parse(record.getAccountType()) == NisaAccountType.GROWTH)
                    .mapToLong(record -> {
                        Long val = record.getTotalPrincipal();
                        return val != null ? val : 0L;
                    }).sum();
        } else if (nisaUserSettingsOpt.isPresent()) {
            Double expectedReturn = nisaUserSettingsOpt.get().getNisaExpectedReturnRate();
            if (expectedReturn == null) {
                expectedReturn = config.getNisaExpectedReturnRate();
            }

            Long growthMonthly = nisaUserSettingsOpt.get().getMonthlyGrowthContribution();
            if (growthMonthly != null && growthMonthly > 0) {
                var growthFund = new SimulationFund(
                        0L,
                        "NISA Growth (Profile Setting)",
                        0L,
                        growthMonthly * 12L,
                        expectedReturn,
                        NisaAccountType.GROWTH,
                        "毎月",
                        growthMonthly);
                context.getNisaFunds().add(growthFund);
            }

            Long tsumitateMonthly = nisaUserSettingsOpt.get().getMonthlyTsumitateContribution();
            if (tsumitateMonthly != null && tsumitateMonthly > 0) {
                var tsumitateFund = new SimulationFund(
                        1L,
                        "NISA Tsumitate (Profile Setting)",
                        0L,
                        tsumitateMonthly * 12L,
                        expectedReturn,
                        NisaAccountType.TSUMITATE,
                        "毎月",
                        tsumitateMonthly);
                context.getNisaFunds().add(tsumitateFund);
            }
        }
        context.setNisaBalance(startingNisaBalance);
        context.setAccumulatedNisaPrincipal(startingNisaPrincipal);
        context.setAccumulatedGrowthNisaPrincipal(startingGrowthNisaPrincipal);

        // Fetch NISA contribution settings
        var nisaSettings = nisaContributionSettingRepository.findAllByOrderByRecordDateDesc();

        // Populate nisaFunds
        for (var record : nisaRecords) {
            Long cmv = record.getCurrentMarketValue();
            long currentBalance = cmv != null ? cmv : 0L;

            // Find contribution setting by fundCode and accountType
            var setting = nisaSettings.stream()
                    .filter(s -> Objects.equals(s.getFundCode(), record.getFundCode())
                            && Objects.equals(s.getAccountType(), record.getAccountType()))
                    .findFirst();

            long annualContribution = 0L;
            String course = null;
            long settingAmount = 0L;

            if (setting.isPresent()) {
                var s = setting.get();
                course = s.getCourse();
                settingAmount = s.getSettingAmount();

                if (SimulationFund.isWeekly(course)) {
                    annualContribution = settingAmount * 52L;
                } else if (SimulationFund.isDaily(course)) {
                    annualContribution = settingAmount * 240L;
                } else {
                    annualContribution = settingAmount * 12L;
                }
            }

            Double expectedReturn = setting.map(s -> s.getExpectedReturnRate())
                    .orElse(null);

            if (expectedReturn == null && nisaUserSettingsOpt.isPresent()) {
                expectedReturn = nisaUserSettingsOpt.get().getNisaExpectedReturnRate();
            }
            if (expectedReturn == null) {
                expectedReturn = config.getNisaExpectedReturnRate();
            }

            var fund = new SimulationFund(
                    record.getId(),
                    record.getFundName(),
                    currentBalance,
                    annualContribution,
                    expectedReturn,
                    NisaAccountType.parse(record.getAccountType()),
                    course,
                    settingAmount);
            context.getNisaFunds().add(fund);
        }
    }
}
