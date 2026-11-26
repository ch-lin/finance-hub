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
package ch.lin.finance.hub.backend.api.app.service.report;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.report.strategy.TransactionReportStrategy;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private TransactionReportStrategy amazonStrategy;

    @Mock
    private TransactionReportStrategy creditCardStrategy;

    @Mock
    private TransactionReportStrategy rakutenStrategy;

    private ReportServiceImpl reportService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        lenient().when(amazonStrategy.getSupportedSource()).thenReturn(TransactionSource.AMAZON);
        lenient().when(creditCardStrategy.getSupportedSource()).thenReturn(TransactionSource.CREDIT_CARD);
        lenient().when(rakutenStrategy.getSupportedSource()).thenReturn(TransactionSource.RAKUTEN);
    }

    @Test
    void testGenerateSummaryReport_AllSources() {
        reportService = new ReportServiceImpl(List.of(amazonStrategy, creditCardStrategy));

        when(amazonStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Amazon Section\n");
            return new BigDecimal("100.50");
        });

        when(creditCardStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Credit Card Section\n");
            return new BigDecimal("50.25");
        });

        GenerateReportCommand command = GenerateReportCommand.builder().build(); // No sources specified = include all
        String report = reportService.generateSummaryReport(command);

        assertTrue(report.contains("# Financial Summary Report:"));
        assertTrue(report.contains("all-time"));
        assertTrue(report.contains("Grand Total Spending: **150.75 JPY**"));
        assertTrue(report.contains(
                "> **Note:** If your Amazon orders were paid using the tracked credit cards, this grand total may contain double-counted amounts."));
        assertTrue(report.contains("Amazon Section"));
        assertTrue(report.contains("Credit Card Section"));

        verify(amazonStrategy).generateReportSection(any(), any(), any(), any());
        verify(creditCardStrategy).generateReportSection(any(), any(), any(), any());
    }

    @Test
    void testGenerateSummaryReport_SpecificSources() {
        reportService = new ReportServiceImpl(List.of(amazonStrategy, creditCardStrategy));

        when(amazonStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Amazon Section\n");
            return new BigDecimal("100.50");
        });

        GenerateReportCommand command = GenerateReportCommand.builder()
                .types(Set.of(ReportType.AMAZON))
                .build();
        String report = reportService.generateSummaryReport(command);

        assertTrue(report.contains("# Financial Summary Report:"));
        assertTrue(report.contains("Grand Total Spending: **100.50 JPY**"));
        assertFalse(report.contains("double-counted amounts"));
        assertTrue(report.contains("Amazon Section"));
        assertFalse(report.contains("Credit Card Section"));

        verify(amazonStrategy).generateReportSection(any(), any(), any(), any());
        verify(creditCardStrategy, never()).generateReportSection(any(), any(), any(), any());
    }

    @Test
    void testGenerateSummaryReport_MissingStrategy() {
        // Only provide amazon strategy
        reportService = new ReportServiceImpl(List.of(amazonStrategy));

        when(amazonStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Amazon Section\n");
            return new BigDecimal("100.50");
        });

        GenerateReportCommand command = GenerateReportCommand.builder().build();
        String report = reportService.generateSummaryReport(command);

        assertTrue(report.contains("Grand Total Spending: **100.50 JPY**"));
        assertTrue(report.contains("Amazon Section"));

        verify(amazonStrategy).generateReportSection(any(), any(), any(), any());
    }

    @Test
    void testGenerateSummaryReport_CreditCardOnly() {
        reportService = new ReportServiceImpl(List.of(amazonStrategy, creditCardStrategy));

        when(creditCardStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Credit Card Section\n");
            return new BigDecimal("50.25");
        });

        GenerateReportCommand command = GenerateReportCommand.builder()
                .types(Set.of(ReportType.CREDIT_CARD))
                .build();
        String report = reportService.generateSummaryReport(command);

        assertTrue(report.contains("Grand Total Spending: **50.25 JPY**"));
        assertFalse(report.contains("double-counted amounts"));
        assertTrue(report.contains("Credit Card Section"));
        assertFalse(report.contains("Amazon Section"));

        verify(creditCardStrategy).generateReportSection(any(), any(), any(), any());
        verify(amazonStrategy, never()).generateReportSection(any(), any(), any(), any());
    }

    @Test
    void testGenerateSummaryReport_ShoppingType() {
        reportService = new ReportServiceImpl(List.of(amazonStrategy, creditCardStrategy, rakutenStrategy));

        when(amazonStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Amazon Section\n");
            return new BigDecimal("100.00");
        });

        when(rakutenStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Rakuten Section\n");
            return new BigDecimal("200.00");
        });

        GenerateReportCommand command = GenerateReportCommand.builder()
                .types(Set.of(ReportType.SHOPPING))
                .build();
        String report = reportService.generateSummaryReport(command);

        assertTrue(report.contains("Grand Total Spending: **300.00 JPY**"));
        assertTrue(report.contains("Amazon Section"));
        assertTrue(report.contains("Rakuten Section"));
        assertFalse(report.contains("Credit Card Section"));

        verify(amazonStrategy).generateReportSection(any(), any(), any(), any());
        verify(rakutenStrategy).generateReportSection(any(), any(), any(), any());
        verify(creditCardStrategy, never()).generateReportSection(any(), any(), any(), any());
    }

    @Test
    void testGenerateSummaryReport_RakutenOnly() {
        reportService = new ReportServiceImpl(List.of(amazonStrategy, creditCardStrategy, rakutenStrategy));

        when(rakutenStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
            StringBuilder sb = invocation.getArgument(3);
            sb.append("Rakuten Section\n");
            return new BigDecimal("75.50");
        });

        GenerateReportCommand command = GenerateReportCommand.builder()
                .types(Set.of(ReportType.RAKUTEN))
                .build();
        String report = reportService.generateSummaryReport(command);

        assertTrue(report.contains("Grand Total Spending: **75.50 JPY**"));
        assertTrue(report.contains("Rakuten Section"));
        assertFalse(report.contains("Amazon Section"));
        assertFalse(report.contains("Credit Card Section"));

        verify(rakutenStrategy).generateReportSection(any(), any(), any(), any());
        verify(amazonStrategy, never()).generateReportSection(any(), any(), any(), any());
        verify(creditCardStrategy, never()).generateReportSection(any(), any(), any(), any());
    }
}
