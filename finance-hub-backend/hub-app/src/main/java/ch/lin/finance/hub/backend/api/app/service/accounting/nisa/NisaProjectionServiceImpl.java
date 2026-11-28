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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.InvestmentReturnCalculator;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

/**
 * Default implementation for calculating future NISA portfolio projections.
 */
@Service
public class NisaProjectionServiceImpl implements NisaProjectionService {

    private static final Logger logger = LoggerFactory.getLogger(NisaProjectionServiceImpl.class);

    private final NisaSnapshotRecordRepository nisaRepository;
    private final FinanceHubConfigService financeHubConfigService;
    private final InvestmentReturnCalculator investmentReturnCalculator;

    /**
     * Constructs a new NisaProjectionServiceImpl.
     *
     * @param nisaRepository          The repository to fetch snapshot records.
     * @param financeHubConfigService The configuration service for fetching
     *                                expected return rates.
     */
    public NisaProjectionServiceImpl(NisaSnapshotRecordRepository nisaRepository,
            FinanceHubConfigService financeHubConfigService, InvestmentReturnCalculator investmentReturnCalculator) {
        this.nisaRepository = nisaRepository;
        this.financeHubConfigService = financeHubConfigService;
        this.investmentReturnCalculator = investmentReturnCalculator;
    }

    @Override
    public List<NisaProjectionYearlyResult> calculateProjection(String userId, int yearsToProject) {
        Optional<NisaSnapshotRecord> latestRecordOpt = nisaRepository
                .findFirstByOwnerUserIdOrderByRecordDateDesc(userId);
        if (latestRecordOpt.isEmpty()) {
            logger.warn("No NISA snapshot records found for user {}. Returning empty projection.", userId);
            return List.of();
        }

        LocalDate latestDate = latestRecordOpt.get().getRecordDate();
        List<NisaSnapshotRecord> latestSnapshots = nisaRepository.findByOwnerUserIdAndRecordDate(userId, latestDate);

        long totalPrincipal = latestSnapshots.stream()
                .mapToLong(record -> {
                    Long principal = record.getTotalPrincipal();
                    return principal != null ? principal : 0L;
                })
                .sum();

        FinanceHubConfig config = financeHubConfigService.getResolvedConfig(null);
        Double configRate = config.getNisaExpectedReturnRate();
        double systemDefaultRate = configRate != null ? configRate : 0.05;

        List<NisaProjectionYearlyResult> projections = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();

        for (int i = 0; i <= yearsToProject; i++) {
            int year = currentYear + i;

            long projectedMarketValue = 0L;
            for (NisaSnapshotRecord record : latestSnapshots) {
                Double recordRate = record.getExpectedReturnRate();
                double rate = recordRate != null ? recordRate : systemDefaultRate;

                Long mv = record.getCurrentMarketValue();
                long initialMarketValue = mv != null ? mv : 0L;

                long fundProjectedValue = investmentReturnCalculator.projectInvestmentBalance(initialMarketValue, 0L,
                        rate, i);
                projectedMarketValue += fundProjectedValue;
            }

            long totalGain = projectedMarketValue - totalPrincipal;
            NisaProjectionYearlyResult result = new NisaProjectionYearlyResult(year, totalPrincipal,
                    projectedMarketValue, totalGain);
            result.setOwnerUserId(latestRecordOpt.get().getOwnerUserId());
            result.setPrivacyLevel(latestRecordOpt.get().getPrivacyLevel());
            projections.add(result);
        }

        return projections;
    }
}
