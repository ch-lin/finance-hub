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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.InvestmentReturnCalculator;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;

@ExtendWith(MockitoExtension.class)
class NisaProjectionServiceTest {

    @Mock
    private NisaSnapshotRecordRepository nisaRepository;

    @Mock
    private FinanceHubConfigService financeHubConfigService;

    @Mock
    private InvestmentReturnCalculator investmentReturnCalculator;

    @InjectMocks
    private NisaProjectionServiceImpl projectionService;

    private FinanceHubConfig mockConfig;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockConfig = new FinanceHubConfig("test-config");
        mockConfig.setNisaExpectedReturnRate(0.05); // 5%
    }

    @Test
    @DisplayName("Should return empty list if no snapshot records exist")
    void calculateProjection_ReturnsEmpty_WhenNoRecords() {
        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.empty());

        List<NisaProjectionYearlyResult> result = projectionService.calculateProjection(30);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should correctly project future value based on 5% return rate")
    void calculateProjection_ReturnsCorrectProjections() {
        LocalDate recordDate = LocalDate.of(2025, 1, 1);

        NisaSnapshotRecord r1 = new NisaSnapshotRecord(recordDate, "NISA", "03311187", "Fund1", 10.0, 100.0, 150.0, 100000L,
                120000L, 20000L);
        NisaSnapshotRecord r2 = new NisaSnapshotRecord(recordDate, "NISA", "03311188", "Fund2", 10.0, 100.0, 150.0, 50000L, 80000L,
                30000L);

        // Total Principal: 150,000
        // Total Market Value: 200,000

        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(r1));
        when(nisaRepository.findByRecordDate(recordDate)).thenReturn(List.of(r1, r2));
        when(financeHubConfigService.getResolvedConfig(any())).thenReturn(mockConfig);

        // Math.round(initialMarketValue * Math.pow(1.0 + rate, i))
        org.mockito.Mockito.lenient().when(investmentReturnCalculator.projectInvestmentBalance(120000L, 0L, 0.05, 0))
                .thenReturn(120000L);
        org.mockito.Mockito.lenient().when(investmentReturnCalculator.projectInvestmentBalance(80000L, 0L, 0.05, 0))
                .thenReturn(80000L);
        org.mockito.Mockito.lenient().when(investmentReturnCalculator.projectInvestmentBalance(120000L, 0L, 0.05, 1))
                .thenReturn(126000L);
        org.mockito.Mockito.lenient().when(investmentReturnCalculator.projectInvestmentBalance(80000L, 0L, 0.05, 1))
                .thenReturn(84000L);
        org.mockito.Mockito.lenient().when(investmentReturnCalculator.projectInvestmentBalance(120000L, 0L, 0.05, 2))
                .thenReturn(132300L);
        org.mockito.Mockito.lenient().when(investmentReturnCalculator.projectInvestmentBalance(80000L, 0L, 0.05, 2))
                .thenReturn(88200L);

        List<NisaProjectionYearlyResult> result = projectionService.calculateProjection(2);

        assertEquals(3, result.size()); // Base year + 2 years = 3 items

        int currentYear = LocalDate.now().getYear();

        // Base Year (0)
        NisaProjectionYearlyResult year0 = result.get(0);
        assertEquals(currentYear, year0.getYear());
        assertEquals(150000L, year0.getProjectedPrincipal());
        assertEquals(200000L, year0.getProjectedMarketValue());
        assertEquals(50000L, year0.getProjectedTotalGain());

        // Year 1 (5% return on 200,000 = 210,000)
        NisaProjectionYearlyResult year1 = result.get(1);
        assertEquals(currentYear + 1, year1.getYear());
        assertEquals(150000L, year1.getProjectedPrincipal());
        assertEquals(210000L, year1.getProjectedMarketValue());
        assertEquals(60000L, year1.getProjectedTotalGain());

        // Year 2 (5% return on 210,000 = 220,500)
        NisaProjectionYearlyResult year2 = result.get(2);
        assertEquals(currentYear + 2, year2.getYear());
        assertEquals(150000L, year2.getProjectedPrincipal());
        assertEquals(220500L, year2.getProjectedMarketValue());
        assertEquals(70500L, year2.getProjectedTotalGain());
    }
}
