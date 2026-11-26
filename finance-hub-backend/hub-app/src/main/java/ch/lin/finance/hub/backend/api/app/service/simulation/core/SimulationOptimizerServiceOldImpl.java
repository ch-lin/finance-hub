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
package ch.lin.finance.hub.backend.api.app.service.simulation.core;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.SolveNisaGoalRequest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import lombok.RequiredArgsConstructor;

/**
 * TODO: 待新版 GlobalOptimizerService 測試驗證完畢後，將此舊版最佳化實作刪除。
 */
@Service
@RequiredArgsConstructor
public class SimulationOptimizerServiceOldImpl implements SimulationOptimizerServiceOld {

    private final AssetSimulationEngine assetSimulationEngine;

    @SuppressWarnings("deprecation")
    @Override
    @Transactional(readOnly = true)
    public Long calculateNisaGoal(SolveNisaGoalRequest request) {
        SimulationData baseData = assetSimulationEngine.fetchSimulationData();

        // Protect Hibernate managed entity from dirty checking
        FinanceHubConfig originalConfig = baseData.getBaseContext().getConfig();
        FinanceHubConfig clonedConfig = new FinanceHubConfig(originalConfig.getName());
        BeanUtils.copyProperties(originalConfig, clonedConfig, "id", "version", "createdAt", "updatedAt");
        baseData.getBaseContext().setConfig(clonedConfig);

        if (request != null) {
            if (request.getOverrideInflationRate() != null) {
                clonedConfig.setInflationRate(request.getOverrideInflationRate());
            }
            if (request.getOverrideNisaReturnRate() != null) {
                clonedConfig.setNisaExpectedReturnRate(request.getOverrideNisaReturnRate());
                // Apply the override to all individual NISA funds so the projector uses it
                for (var fund : baseData.getBaseContext().getNisaFunds()) {
                    fund.setExpectedReturnRate(request.getOverrideNisaReturnRate());
                }
            }
        }

        long low = 0;
        long high = 300_000L; // Max monthly NISA 300k
        long best = -1L;
        Long reqSafetyNet = request != null ? request.getSafetyNetAmount() : null;
        long safetyNet = reqSafetyNet != null ? reqSafetyNet : 0L;
        int defaultRetirementAge = baseData.getBaseContext().getProfile() != null
                && baseData.getBaseContext().getProfile().getTargetRetirementAge() != null
                        ? baseData.getBaseContext().getProfile().getTargetRetirementAge()
                        : baseData.getBaseContext().getConfig().getTargetRetirementAge();

        Integer reqTargetAge = request != null ? request.getTargetAge() : null;
        int targetAge = reqTargetAge != null ? reqTargetAge : defaultRetirementAge;

        if (targetAge <= baseData.getBaseContext().getCurrentAge()) {
            throw new IllegalArgumentException("Target age must be greater than current age.");
        }

        while (low <= high) {
            long mid = low + (high - low) / 2;

            SimulationData clone;
            try {
                clone = baseData.cloneContext();
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException("Failed to clone SimulationContext", e);
            }
            SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
                    null);
            List<AssetSimulationYearlyResult> simulation = assetSimulationEngine.runSimulationWithData(
                    clone, targetAge, overrides);

            boolean noBankruptcy = simulation.stream()
                    .allMatch(dto -> dto.getProjectedLiquidCash() >= 0 && dto.getProjectedNetWorth() >= safetyNet);

            if (noBankruptcy) {
                best = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return best == -1L ? null : best;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findBestMortgageEarlyRepaymentYear(int targetAge) {
        SimulationData baseData = assetSimulationEngine.fetchSimulationData();
        List<MortgageRecord> mortgages = baseData.getAllMortgages();
        if (mortgages == null || mortgages.isEmpty()) {
            return null; // No mortgage to repay
        }

        int currentYear = baseData.getBaseContext().getCurrentYear();
        int endYear = currentYear;
        for (MortgageRecord m : mortgages) {
            int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
            if (mEndYear > endYear) {
                endYear = mEndYear;
            }
        }

        Integer bestYear = null;
        long maxNetWorth = Long.MIN_VALUE;
        long safetyNet = baseData.getBaseContext().getBaselineMonthlyExpenses() * 24; // 2 years safety net

        // Test every year from now until the mortgage naturally ends, plus a "never"
        // scenario (null)
        for (int year = currentYear; year <= endYear + 1; year++) {
            Integer testYear = (year > endYear) ? null : year;

            SimulationData clone;
            try {
                clone = baseData.cloneContext();
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException("Failed to clone SimulationContext", e);
            }

            // NOTE: mortgageEarlyRepaymentYear removed; this old optimizer is deprecated.
            SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
                    null);
            List<AssetSimulationYearlyResult> simulation = assetSimulationEngine.runSimulationWithData(clone, targetAge,
                    overrides);

            // Filter bankrupt scenarios
            boolean noBankruptcy = simulation.stream()
                    .allMatch(dto -> dto.getProjectedLiquidCash() >= 0 && dto.getProjectedNetWorth() >= safetyNet);
            if (!noBankruptcy) {
                continue;
            }

            // Check final net worth
            long finalNetWorth = simulation.get(simulation.size() - 1).getProjectedNetWorth();
            if (finalNetWorth > maxNetWorth) {
                maxNetWorth = finalNetWorth;
                bestYear = testYear;
            }
        }

        return bestYear;
    }
}
