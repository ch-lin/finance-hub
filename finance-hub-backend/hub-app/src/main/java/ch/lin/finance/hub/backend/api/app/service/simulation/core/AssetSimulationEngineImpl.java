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

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.CareerEventRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.LifeEventRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.FundOverrideApplier;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.factory.SimulationContextFactory;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline.YearlySimulationPipeline;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.AssetSimulationYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.utils.SimulationParameterResolver;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSimulationEngineImpl implements AssetSimulationEngine {

    private final SimulationContextFactory simulationContextFactory;
    private final CareerEventRepository careerEventRepository;
    private final LifeEventRepository lifeEventRepository;
    private final MortgageRecordRepository mortgageRecordRepository;

    private final FundOverrideApplier fundOverrideApplier;
    private final YearlySimulationPipeline yearlySimulationPipeline;

    @Override
    @Transactional(readOnly = true)
    public List<AssetSimulationYearlyResult> runSimulation(int targetAge) {
        return runSimulationWithOverrides(targetAge, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetSimulationYearlyResult> runSimulationWithOverrides(int targetAge,
            SimulationOverrides overrides) {
        SimulationData data = fetchSimulationData();
        return runSimulationWithData(data, targetAge, overrides);
    }

    @Override
    @Transactional(readOnly = true)
    public SimulationData fetchSimulationData() {
        String userId = SecurityUtil.getCurrentUserId();
        SimulationContext baseContext = simulationContextFactory.createContext();
        List<CareerEvent> careerEvents = careerEventRepository.findAllByOwnerUserIdOrderByEventYearMonthAsc(userId);
        List<LifeEvent> lifeEvents = lifeEventRepository.findAllByOwnerUserIdOrderByStartYearMonthAsc(userId);
        List<MortgageRecord> allMortgages = mortgageRecordRepository.findAllByOwnerUserId(userId);
        YearMonth nowMonth = YearMonth.now();
        return new SimulationData(baseContext, careerEvents, lifeEvents, allMortgages, nowMonth);
    }

    @Override
    public List<AssetSimulationYearlyResult> runSimulationWithData(SimulationData data, int targetAge,
            SimulationOverrides overrides) {
        return runSimulationInternalMem(
                data.getBaseContext(),
                targetAge,
                overrides,
                data.getCareerEvents(),
                data.getLifeEvents(),
                data.getAllMortgages(),
                data.getNowMonth());
    }

    private List<AssetSimulationYearlyResult> runSimulationInternalMem(
            SimulationContext context,
            int targetAge,
            SimulationOverrides overrides,
            List<CareerEvent> careerEvents,
            List<LifeEvent> lifeEvents,
            List<MortgageRecord> allMortgages,
            YearMonth nowMonth) {

        int dcUnlockAge = SimulationParameterResolver.resolveDcUnlockAge(context.getConfig());
        int idecoUnlockAge = SimulationParameterResolver.resolveIdecoUnlockAge(context.getConfig());
        Integer retirementOverride = overrides != null ? overrides.targetRetirementAgeOverride() : null;
        int targetRetirementAge = SimulationParameterResolver.resolveRetirementAge(context.getProfile(),
                context.getConfig(), retirementOverride);
        Integer idecoStartOverride = overrides != null ? overrides.idecoStartAgeOverride() : null;
        int taxAdvantagedWithdrawalAge = SimulationParameterResolver
                .resolveTaxAdvantagedWithdrawalAge(context.getProfile(), dcUnlockAge, idecoUnlockAge,
                        idecoStartOverride, targetRetirementAge);
        Integer pensionOverride = overrides != null ? overrides.pensionStartAgeOverride() : null;
        int pensionStartAge = SimulationParameterResolver.resolvePensionStartAge(context.getProfile(),
                context.getConfig(), pensionOverride);

        if (targetAge < context.getCurrentAge()) {
            throw new IllegalArgumentException(String.format("targetAge (%d) cannot be less than currentAge (%d)",
                    targetAge, context.getCurrentAge()));
        }
        if (targetRetirementAge < context.getCurrentAge()) {
            throw new IllegalArgumentException(
                    String.format("targetRetirementAge (%d) cannot be less than currentAge (%d)", targetRetirementAge,
                            context.getCurrentAge()));
        }
        if (pensionStartAge < targetRetirementAge) {
            throw new IllegalArgumentException(
                    String.format("pensionStartAge (%d) cannot be earlier than targetRetirementAge (%d)",
                            pensionStartAge, targetRetirementAge));
        }

        List<AssetSimulationYearlyResult> simulation = new ArrayList<>();

        int initialAge = context.getCurrentAge();

        // Apply fund-level overrides (return rate, contribution) before the simulation
        // loop. Delegated to FundOverrideApplier to keep this method focused.
        fundOverrideApplier.applyOverrides(context, overrides);

        for (int age = context.getCurrentAge(); age <= targetAge; age++) {
            AssetSimulationYearlyResult dto = yearlySimulationPipeline.processSimulationYear(context, overrides,
                    careerEvents, lifeEvents,
                    allMortgages, nowMonth, dcUnlockAge, idecoUnlockAge, taxAdvantagedWithdrawalAge,
                    targetRetirementAge, pensionStartAge, initialAge, age);
            simulation.add(dto);
        }

        return simulation;
    }

}
