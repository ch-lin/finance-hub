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

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.TaxDateUtils;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DefinedContributionContextBuilder {

    private final DefinedContributionAccountSnapshotRepository dcRepository;
    private final DefinedContributionUserSettingsRepository dcUserSettingsRepository;

    public void initializeDefinedContributionContext(SimulationContext context, FinanceHubConfig config,
            String userId) {
        long startingDcBalance = 0L;
        var latestDcOpt = dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc(
                userId, DefinedContributionType.CORPORATE_DC);
        var dcSettingsOpt = dcUserSettingsRepository.findByOwnerUserId(userId);
        if (latestDcOpt.isPresent()) {
            var accountSnapshot = latestDcOpt.get();

            Integer dcOverrideVal = (dcSettingsOpt.isPresent()
                    && Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden()))
                            ? dcSettingsOpt.get().getDcContributionYearsOverride()
                            : null;

            if (dcOverrideVal != null) {
                context.setDcContributionYears(dcOverrideVal);
            } else if (accountSnapshot.getEnrollmentDate() != null) {
                context.setDcEnrollmentDate(accountSnapshot.getEnrollmentDate());
                context.setDcContributionYears(
                        TaxDateUtils.calculateTaxServiceYears(accountSnapshot.getEnrollmentDate(), LocalDate.now()));
            }

            Long mec = accountSnapshot.getMonthlyEmployerContribution();
            long employerContribution = mec != null ? mec : 0L;
            context.setDcMonthlyEmployerContribution(employerContribution);

            for (var record : accountSnapshot.getFunds()) {
                Long cmv = record.getCurrentMarketValue();
                long currentBalance = cmv != null ? cmv : 0L;
                Long mca = record.getMonthlyContributionAmount();
                long monthlyContribution = mca != null ? mca : 0L;
                long annualContribution = monthlyContribution * 12L;
                Double expectedReturnRate = record.getExpectedReturnRate();
                if (expectedReturnRate == null) {
                    expectedReturnRate = config.getIdecoExpectedReturnRate();
                }

                var fund = new SimulationFund(
                        record.getId(),
                        record.getFundName(),
                        currentBalance,
                        annualContribution,
                        expectedReturnRate,
                        null,
                        "毎月",
                        monthlyContribution);
                context.getDcFunds().add(fund);
                startingDcBalance += currentBalance;
            }
        } else if (dcSettingsOpt.isPresent() && dcSettingsOpt.get().getMonthlyDcMatchingContribution() != null
                && dcSettingsOpt.get().getMonthlyDcMatchingContribution() > 0) {
            if (Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden())) {
                Integer overrideVal = dcSettingsOpt.get().getDcContributionYearsOverride();
                if (overrideVal != null) {
                    context.setDcContributionYears(overrideVal);
                }
            }

            // TODO: In the future, we might want to separate the expected return rate
            // configuration for Corporate DC and iDeCo if they are managed by different
            // brokerages with different fund performances.
            Double expectedReturn = config.getIdecoExpectedReturnRate();
            var settingsDerivedFund = new SimulationFund(
                    0L,
                    "Corporate DC (Profile Setting)",
                    0L,
                    dcSettingsOpt.get().getMonthlyDcMatchingContribution() * 12L,
                    expectedReturn,
                    null,
                    "毎月",
                    dcSettingsOpt.get().getMonthlyDcMatchingContribution());
            context.getDcFunds().add(settingsDerivedFund);
        }
        context.setDcBalance(startingDcBalance);

        long startingIdecoBalance = 0L;
        var latestIdecoOpt = dcRepository.findFirstByOwnerUserIdAndTypeOrderByRecordDateDesc(
                userId, DefinedContributionType.IDECO);
        if (latestIdecoOpt.isPresent()) {
            var accountSnapshot = latestIdecoOpt.get();

            Integer idecoOverrideVal = (dcSettingsOpt.isPresent()
                    && Boolean.TRUE.equals(dcSettingsOpt.get().getIsIdecoContributionYearsOverridden()))
                            ? dcSettingsOpt.get().getIdecoContributionYearsOverride()
                            : null;

            if (idecoOverrideVal != null) {
                context.setIdecoContributionYears(idecoOverrideVal);
            } else if (accountSnapshot.getEnrollmentDate() != null) {
                context.setIdecoEnrollmentDate(accountSnapshot.getEnrollmentDate());
                context.setIdecoContributionYears(
                        TaxDateUtils.calculateTaxServiceYears(accountSnapshot.getEnrollmentDate(), LocalDate.now()));
            }

            for (var record : accountSnapshot.getFunds()) {
                Long cmv = record.getCurrentMarketValue();
                long currentBalance = cmv != null ? cmv : 0L;
                Long mca = record.getMonthlyContributionAmount();
                long monthlyContribution = mca != null ? mca : 0L;
                long annualContribution = monthlyContribution * 12L;

                Double expectedReturnRate = record.getExpectedReturnRate();
                if (expectedReturnRate == null && dcSettingsOpt.isPresent()) {
                    expectedReturnRate = dcSettingsOpt.get().getIdecoExpectedReturnRate();
                }
                if (expectedReturnRate == null) {
                    expectedReturnRate = config.getIdecoExpectedReturnRate();
                }

                var fund = new SimulationFund(
                        record.getId(),
                        record.getFundName(),
                        currentBalance,
                        annualContribution,
                        expectedReturnRate,
                        null,
                        "毎月",
                        monthlyContribution);
                context.getIdecoFunds().add(fund);
                startingIdecoBalance += currentBalance;
            }
        } else if (dcSettingsOpt.isPresent() && dcSettingsOpt.get().getMonthlyIdecoContribution() != null
                && dcSettingsOpt.get().getMonthlyIdecoContribution() > 0) {
            if (Boolean.TRUE.equals(dcSettingsOpt.get().getIsIdecoContributionYearsOverridden())) {
                Integer overrideVal = dcSettingsOpt.get().getIdecoContributionYearsOverride();
                if (overrideVal != null) {
                    context.setIdecoContributionYears(overrideVal);
                }
            }

            Double expectedReturn = dcSettingsOpt.get().getIdecoExpectedReturnRate();
            if (expectedReturn == null) {
                expectedReturn = config.getIdecoExpectedReturnRate();
            }
            var settingsDerivedFund = new SimulationFund(
                    0L,
                    "iDeCo (Profile Setting)",
                    0L,
                    dcSettingsOpt.get().getMonthlyIdecoContribution() * 12L,
                    expectedReturn,
                    null,
                    "毎月",
                    dcSettingsOpt.get().getMonthlyIdecoContribution());
            context.getIdecoFunds().add(settingsDerivedFund);
        }
        context.setIdecoBalance(startingIdecoBalance);
    }

}
