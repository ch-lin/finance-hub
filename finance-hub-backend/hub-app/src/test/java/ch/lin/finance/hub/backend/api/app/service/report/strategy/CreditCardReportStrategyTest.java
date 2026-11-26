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
package ch.lin.finance.hub.backend.api.app.service.report.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.service.report.ReportDateRange;
import ch.lin.finance.hub.backend.api.app.service.report.ReportSortOption;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
class CreditCardReportStrategyTest {

    @Mock
    private CreditCardTransactionRepository repository;

    private CreditCardReportStrategy strategy;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        strategy = new CreditCardReportStrategy(repository);
    }

    @Test
    void testGetSupportedSource() {
        assertEquals(TransactionSource.CREDIT_CARD, strategy.getSupportedSource());
    }

    @Test
    void testGenerateReportSection_EmptyTransactions() {
        when(repository.findByTransactionDateBetween(any(), any())).thenReturn(new ArrayList<>());

        ReportDateRange dateRange = new ReportDateRange(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31));
        StringBuilder sb = new StringBuilder();

        BigDecimal result = strategy.generateReportSection(dateRange, ReportSortOption.DATE_ASC, "testUser", sb);

        assertEquals(BigDecimal.ZERO, result);
        assertTrue(sb.toString().contains("No credit card transactions found for this period."));
    }

    @Test
    void testGenerateReportSection_WithTransactions_SortingAndDetails() {
        CreditCardTransaction tx1 = new CreditCardTransaction("Visa", LocalDate.of(2023, 1, 10), "Store A", 1000);

        CreditCardTransaction tx2 = new CreditCardTransaction("MasterCard", LocalDate.of(2023, 1, 5), "Store B", 3000);

        CreditCardTransaction tx3 = new CreditCardTransaction("Visa", LocalDate.of(2023, 1, 20), "Store C", 500);

        List<CreditCardTransaction> transactions = new ArrayList<>(List.of(tx1, tx2, tx3));
        when(repository.findByTransactionDateBetween(any(), any())).thenReturn(transactions);

        ReportDateRange dateRange = new ReportDateRange(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31));

        // --- Test DATE_ASC ---
        StringBuilder sbAsc = new StringBuilder();
        BigDecimal resultAsc = strategy.generateReportSection(dateRange, ReportSortOption.DATE_ASC, "testUser", sbAsc);
        assertEquals(new BigDecimal("4500"), resultAsc);
        String reportAsc = sbAsc.toString();

        // Check order: TX-2 (1/5), TX-1 (1/10), TX-3 (1/20) -> Store B, Store A, Store
        // C
        assertTrue(reportAsc.indexOf("Store B") < reportAsc.indexOf("Store A"));
        assertTrue(reportAsc.indexOf("Store A") < reportAsc.indexOf("Store C"));

        // Check spending by card is sorted descending by value: Visa(1500),
        // MasterCard(3000) -> MasterCard first
        assertTrue(reportAsc.indexOf("**MasterCard:** 3000 JPY") < reportAsc.indexOf("**Visa:** 1500 JPY"));
        assertTrue(reportAsc.contains("- **Total Credit Card Spending:** 4500 JPY"));

        // --- Test DATE_DESC ---
        StringBuilder sbDesc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.DATE_DESC, "testUser", sbDesc);
        String reportDesc = sbDesc.toString();
        assertTrue(reportDesc.indexOf("Store C") < reportDesc.indexOf("Store A"));
        assertTrue(reportDesc.indexOf("Store A") < reportDesc.indexOf("Store B"));

        // --- Test AMOUNT_ASC ---
        StringBuilder sbAmtAsc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.AMOUNT_ASC, "testUser", sbAmtAsc);
        String reportAmtAsc = sbAmtAsc.toString();
        // Amounts: 500, 1000, 3000 -> Store C, Store A, Store B
        assertTrue(reportAmtAsc.indexOf("Store C") < reportAmtAsc.indexOf("Store A"));
        assertTrue(reportAmtAsc.indexOf("Store A") < reportAmtAsc.indexOf("Store B"));

        // --- Test AMOUNT_DESC ---
        StringBuilder sbAmtDesc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.AMOUNT_DESC, "testUser", sbAmtDesc);
        String reportAmtDesc = sbAmtDesc.toString();
        // Amounts: 3000, 1000, 500 -> Store B, Store A, Store C
        assertTrue(reportAmtDesc.indexOf("Store B") < reportAmtDesc.indexOf("Store A"));
        assertTrue(reportAmtDesc.indexOf("Store A") < reportAmtDesc.indexOf("Store C"));
    }
}
