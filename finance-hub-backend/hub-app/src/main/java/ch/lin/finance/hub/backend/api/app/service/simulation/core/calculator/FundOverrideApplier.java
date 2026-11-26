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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.NisaAccountType;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies {@link SimulationOverrides#fundOverrides()} to the funds stored in
 * {@link SimulationContext} before the simulation loop begins.
 *
 * <p>
 * Override priority (highest to lowest):
 * <ol>
 * <li>Caller-supplied {@code FundOverride} (keyed by
 * {@code accountType + fundId})</li>
 * <li>Value already stored on the {@link SimulationFund} from the DB</li>
 * </ol>
 *
 * <p>
 * Only non-null override fields are applied; leaving a field null means "use DB
 * value".
 */
@Slf4j
@Component
public class FundOverrideApplier {

    /**
     * Applies any fund-level overrides from {@code overrides} to the context's fund
     * lists.
     * This must be called once, before the per-year simulation loop.
     *
     * @param context   the simulation context holding nisaFunds, dcFunds,
     *                  idecoFunds
     * @param overrides the overrides supplied by the caller (may be null)
     */
    public void applyOverrides(SimulationContext context, SimulationOverrides overrides) {
        if (overrides == null) {
            return;
        }

        // 1. Apply global NISA contribution test goal (if any)
        if (overrides.testNisaGrowthMonthlyContribution() != null) {
            applyGlobalContribution(context.getNisaFunds(), overrides.testNisaGrowthMonthlyContribution(),
                    NisaAccountType.GROWTH, context.getConfig().getNisaExpectedReturnRate());
        }
        if (overrides.testNisaTsumitateMonthlyContribution() != null) {
            applyGlobalContribution(context.getNisaFunds(), overrides.testNisaTsumitateMonthlyContribution(),
                    NisaAccountType.TSUMITATE, context.getConfig().getNisaExpectedReturnRate());
        }

        // 2. Apply explicit per-fund overrides (if any)
        if (overrides.fundOverrides() != null && !overrides.fundOverrides().isEmpty()) {
            // Key: "NISA:42", "CORPORATE_DC:7", "IDECO:3" etc.
            // Using composite key prevents cross-table id collisions:
            // NISA uses NisaSnapshotRecord.id, DC/iDeCo use
            // DefinedContributionFundSnapshot.id.
            Map<String, SimulationOverrides.FundOverride> overrideMap = overrides.fundOverrides().stream()
                    .filter(fo -> fo.fundId() != null && fo.accountType() != null)
                    .collect(Collectors.toMap(
                            fo -> fo.accountType().toUpperCase() + ":" + fo.fundId(),
                            fo -> fo));

            applyToFunds(context.getNisaFunds(), "NISA", overrideMap);
            applyToFunds(context.getDcFunds(), "CORPORATE_DC", overrideMap);
            applyToFunds(context.getIdecoFunds(), "IDECO", overrideMap);
        }
    }

    private void applyGlobalContribution(List<SimulationFund> allNisaFunds, long testMonthlyContribution,
            NisaAccountType accountType, Double defaultReturnRate) {
        List<SimulationFund> targetFunds = allNisaFunds.stream()
                .filter(f -> f.getNisaAccountType() == accountType)
                .collect(Collectors.toList());

        long totalCurrentAnnual = targetFunds.stream()
                .mapToLong(f -> f.getAnnualContribution())
                .sum();

        if (totalCurrentAnnual > 0) {
            // Distribute proportionally
            long allocatedSoFar = 0;
            for (int i = 0; i < targetFunds.size(); i++) {
                SimulationFund fund = targetFunds.get(i);
                if (i == targetFunds.size() - 1) {
                    long monthly = testMonthlyContribution - allocatedSoFar;
                    fund.setAnnualContribution(monthly * 12);
                    fund.setCourse("毎月");
                    fund.setSettingAmount(monthly);
                } else {
                    double ratio = (double) fund.getAnnualContribution() / totalCurrentAnnual;
                    long allocatedMonthly = Math.round(testMonthlyContribution * ratio);
                    fund.setAnnualContribution(allocatedMonthly * 12);
                    fund.setCourse("毎月");
                    fund.setSettingAmount(allocatedMonthly);
                    allocatedSoFar += allocatedMonthly;
                }
            }
        } else {
            // Zero current contribution
            if (targetFunds.isEmpty()) {
                // Create a virtual fund to absorb the test contribution
                long newFundId = allNisaFunds.stream().mapToLong(f -> f.getFundId()).max().orElse(0L) + 1L;
                SimulationFund virtualFund = new SimulationFund(
                        newFundId,
                        "Simulated NISA " + accountType.name() + " (Virtual)",
                        0L,
                        testMonthlyContribution * 12,
                        defaultReturnRate,
                        accountType,
                        "毎月",
                        testMonthlyContribution);
                allNisaFunds.add(virtualFund);
            } else {
                // Distribute evenly among existing funds
                long perFund = testMonthlyContribution / targetFunds.size();
                long allocatedSoFar = 0;
                for (int i = 0; i < targetFunds.size(); i++) {
                    SimulationFund fund = targetFunds.get(i);
                    if (i == targetFunds.size() - 1) {
                        long monthly = testMonthlyContribution - allocatedSoFar;
                        fund.setAnnualContribution(monthly * 12);
                        fund.setCourse("毎月");
                        fund.setSettingAmount(monthly);
                    } else {
                        fund.setAnnualContribution(perFund * 12);
                        fund.setCourse("毎月");
                        fund.setSettingAmount(perFund);
                        allocatedSoFar += perFund;
                    }
                }
            }
        }
    }

    private void applyToFunds(
            List<SimulationFund> funds,
            String accountType,
            Map<String, SimulationOverrides.FundOverride> overrideMap) {

        for (SimulationFund fund : funds) {
            String key = accountType + ":" + fund.getFundId();
            SimulationOverrides.FundOverride ov = overrideMap.get(key);
            if (ov == null)
                continue;

            if (ov.returnRateOverride() != null) {
                log.debug("[FundOverrideApplier] {} fundId={} returnRate: {} -> {}",
                        accountType, fund.getFundId(), fund.getExpectedReturnRate(), ov.returnRateOverride());
                fund.setExpectedReturnRate(ov.returnRateOverride());
            }
            if (ov.contributionOverride() != null) {
                // contributionOverride is monthly; store as annual for the simulation loop
                long newAnnual = ov.contributionOverride() * 12;
                log.debug("[FundOverrideApplier] {} fundId={} annualContribution: {} -> {}",
                        accountType, fund.getFundId(), fund.getAnnualContribution(), newAnnual);
                fund.setAnnualContribution(newAnnual);
                fund.setCourse("毎月");
                fund.setSettingAmount(ov.contributionOverride());
            }
        }
    }
}
