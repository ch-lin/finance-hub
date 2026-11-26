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

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.report.ReportDateRange;
import ch.lin.finance.hub.backend.api.app.service.report.ReportSortOption;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonPayment;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
class AmazonReportStrategyTest {

    @Mock
    private AmazonOrderRepository repository;

    private AmazonReportStrategy strategy;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        strategy = new AmazonReportStrategy(repository);
    }

    @Test
    void testGetSupportedSource() {
        assertEquals(TransactionSource.AMAZON, strategy.getSupportedSource());
    }

    @Test
    void testGenerateReportSection_EmptyOrders() {
        when(repository.findByOrderDateBetween(any(), any())).thenReturn(new ArrayList<>());

        ReportDateRange dateRange = new ReportDateRange(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31));
        StringBuilder sb = new StringBuilder();

        BigDecimal result = strategy.generateReportSection(dateRange, ReportSortOption.DATE_ASC, "testUser", sb);

        assertEquals(BigDecimal.ZERO, result);
        assertTrue(sb.toString().contains("No Amazon orders found for this period."));
    }

    @Test
    void testGenerateReportSection_WithOrders_SortingAndDetails() {
        // Prepare some items
        AmazonItem item1 = new AmazonItem("Short Name");
        item1.setItemId("12345");
        item1.setUnitPrice(new BigDecimal("1000"));
        item1.setQuantity(1);

        AmazonItem item2 = new AmazonItem("Item | name");
        item2.setItemId(""); // Blank ID
        item2.setUnitPrice(new BigDecimal("2000"));
        item2.setQuantity(2);

        AmazonItem item3 = new AmazonItem("No items list");
        item3.setUnitPrice(new BigDecimal("500"));
        item3.setQuantity(1);

        AmazonItem item4 = new AmazonItem("Very long item name that exceeds forty characters to test truncation");
        item4.setItemId("999");
        item4.setUnitPrice(new BigDecimal("100"));
        item4.setQuantity(1);

        AmazonPayment payment1 = new AmazonPayment("Visa");

        // Order 1: 1 item, has payments
        AmazonOrder order1 = new AmazonOrder("O-001", LocalDate.of(2023, 1, 10));
        order1.setTotalAmount(new BigDecimal("1000"));
        order1.setBilledAmount(new BigDecimal("1000"));
        order1.setGiftCardAmount(new BigDecimal("0"));
        order1.setDPointsUsed(new BigDecimal("0"));
        order1.setPointsUsed(new BigDecimal("0"));
        order1.setDiscounts(new BigDecimal("0"));
        order1.addItem(item1);
        order1.addPayment(payment1);

        // Order 2: Multiple items (pipe, no ID), no payments
        AmazonOrder order2 = new AmazonOrder("O-002", LocalDate.of(2023, 1, 5));
        order2.setTotalAmount(new BigDecimal("4500")); // 2000*2 + 500
        order2.setBilledAmount(new BigDecimal("4000"));
        order2.setGiftCardAmount(new BigDecimal("500"));
        order2.setDPointsUsed(new BigDecimal("0"));
        order2.setPointsUsed(new BigDecimal("0"));
        order2.setDiscounts(new BigDecimal("0"));
        order2.addItem(item2);
        order2.addItem(item3);

        // Order 3: No items, empty payments
        AmazonOrder order3 = new AmazonOrder("O-003", LocalDate.of(2023, 1, 20));
        order3.setTotalAmount(new BigDecimal("0"));
        order3.setBilledAmount(new BigDecimal("0"));
        order3.setGiftCardAmount(new BigDecimal("0"));
        order3.setDPointsUsed(new BigDecimal("0"));
        order3.setPointsUsed(new BigDecimal("0"));
        order3.setDiscounts(new BigDecimal("0"));
        org.springframework.test.util.ReflectionTestUtils.setField(order3, "payments", new ArrayList<>()); // explicitly
                                                                                                           // empty

        // Order 4: Null items and null payments
        AmazonOrder order4 = new AmazonOrder("O-004", LocalDate.of(2023, 1, 2));
        order4.setTotalAmount(new BigDecimal("500"));
        order4.setBilledAmount(new BigDecimal("500"));
        order4.setGiftCardAmount(new BigDecimal("0"));
        order4.setDPointsUsed(new BigDecimal("0"));
        order4.setPointsUsed(new BigDecimal("0"));
        order4.setDiscounts(new BigDecimal("0"));
        org.springframework.test.util.ReflectionTestUtils.setField(order4, "items", null);
        org.springframework.test.util.ReflectionTestUtils.setField(order4, "payments", null);

        // Order 5: Long name
        AmazonOrder order5 = new AmazonOrder("O-005", LocalDate.of(2023, 1, 15));
        order5.setTotalAmount(new BigDecimal("100"));
        order5.setBilledAmount(new BigDecimal("100"));
        order5.setGiftCardAmount(new BigDecimal("0"));
        order5.setDPointsUsed(new BigDecimal("0"));
        order5.setPointsUsed(new BigDecimal("0"));
        order5.setDiscounts(new BigDecimal("0"));
        order5.addItem(item4);

        List<AmazonOrder> orders = new ArrayList<>(List.of(order1, order2, order3, order4, order5));
        when(repository.findByOrderDateBetween(any(), any())).thenReturn(orders);

        ReportDateRange dateRange = new ReportDateRange(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31));

        // --- Test DATE_ASC ---
        StringBuilder sbAsc = new StringBuilder();
        BigDecimal resultAsc = strategy.generateReportSection(dateRange, ReportSortOption.DATE_ASC, "testUser", sbAsc);
        assertEquals(new BigDecimal("5600"), resultAsc);
        String reportAsc = sbAsc.toString();

        // Check items output
        assertTrue(reportAsc.contains("No items")); // order3 & order4
        assertTrue(reportAsc.contains("Short Name")); // order1
        assertTrue(reportAsc.contains("Item \\| name and 1 other item(s)")); // order2 table
        assertTrue(reportAsc.contains("Very long item name that exceeds fort...")); // order5 table truncated
        assertTrue(reportAsc.contains("(https://www.amazon.co.jp/dp/12345)")); // link for item1
        assertTrue(reportAsc.contains("- Item | name (2000 JPY x 2)")); // item2 has no link, full name in details
        assertTrue(reportAsc.contains("**Payment Method:** Visa"));

        // --- Test DATE_DESC ---
        StringBuilder sbDesc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.DATE_DESC, "testUser", sbDesc);
        String reportDesc = sbDesc.toString();
        assertTrue(reportDesc.indexOf("O-003") < reportDesc.indexOf("O-001"));

        // --- Test AMOUNT_ASC ---
        // total amounts: O-001(1000), O-002(4500), O-003(0), O-004(500), O-005(100)
        // Ascending order: O-003, O-005, O-004, O-001, O-002
        StringBuilder sbAmtAsc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.AMOUNT_ASC, "testUser", sbAmtAsc);
        String reportAmtAsc = sbAmtAsc.toString();
        // Amounts: O-003(0), O-004(500), O-001(1000), O-002(4500)
        assertTrue(reportAmtAsc.indexOf("O-003") < reportAmtAsc.indexOf("O-004"));
        assertTrue(reportAmtAsc.indexOf("O-004") < reportAmtAsc.indexOf("O-001"));
        assertTrue(reportAmtAsc.indexOf("O-001") < reportAmtAsc.indexOf("O-002"));

        // --- Test AMOUNT_DESC ---
        StringBuilder sbAmtDesc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.AMOUNT_DESC, "testUser", sbAmtDesc);
        String reportAmtDesc = sbAmtDesc.toString();
        assertTrue(reportAmtDesc.indexOf("O-002") < reportAmtDesc.indexOf("O-001"));
        assertTrue(reportAmtDesc.indexOf("O-001") < reportAmtDesc.indexOf("O-004"));
        assertTrue(reportAmtDesc.indexOf("O-004") < reportAmtDesc.indexOf("O-003"));
    }
}
