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
import org.springframework.test.util.ReflectionTestUtils;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.RakutenOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.report.ReportDateRange;
import ch.lin.finance.hub.backend.api.app.service.report.ReportSortOption;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenPayment;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
class RakutenReportStrategyTest {

    @Mock
    private RakutenOrderRepository repository;

    private RakutenReportStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RakutenReportStrategy(repository);
    }

    @Test
    void testGetSupportedSource() {
        assertEquals(TransactionSource.RAKUTEN, strategy.getSupportedSource());
    }

    @Test
    void testGenerateReportSection_EmptyOrders() {
        when(repository.findByOrderDateBetween(any(), any())).thenReturn(new ArrayList<>());

        ReportDateRange dateRange = new ReportDateRange(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31));
        StringBuilder sb = new StringBuilder();

        BigDecimal result = strategy.generateReportSection(dateRange, ReportSortOption.DATE_ASC, "testUser", sb);

        assertEquals(BigDecimal.ZERO, result);
        assertTrue(sb.toString().contains("No Rakuten orders found for this period."));
    }

    @Test
    void testGenerateReportSection_WithOrders_SortingAndDetails() {
        // Prepare items
        RakutenItem item1 = new RakutenItem("Short Name");
        item1.setSeller("shop-abc");
        item1.setItemId("item-123");
        item1.setUnitPrice(new BigDecimal("1000"));
        item1.setQuantity(1);

        RakutenItem item2 = new RakutenItem("Item | name");
        item2.setUnitPrice(new BigDecimal("2000"));
        item2.setQuantity(2);

        RakutenItem item3 = new RakutenItem("Secondary item");
        item3.setUnitPrice(new BigDecimal("500"));
        item3.setQuantity(1);

        RakutenItem item4 = new RakutenItem("Very long item name that exceeds forty characters to test truncation");
        item4.setSeller("shop-xyz");
        item4.setItemId("item-999");
        item4.setUnitPrice(new BigDecimal("100"));
        item4.setQuantity(1);

        RakutenPayment payment1 = new RakutenPayment("Credit Card");

        // Order 1: 1 item with seller/itemId, has payments
        RakutenOrder order1 = new RakutenOrder("R-001", LocalDate.of(2023, 1, 10));
        order1.setTotalAmount(new BigDecimal("1000"));
        order1.setBilledAmount(new BigDecimal("1000"));
        order1.setPointsUsed(new BigDecimal("0"));
        order1.setDiscounts(new BigDecimal("0"));
        order1.addItem(item1);
        order1.addPayment(payment1);

        // Order 2: Multiple items (pipe, no seller/itemId), no payments
        RakutenOrder order2 = new RakutenOrder("R-002", LocalDate.of(2023, 1, 5));
        order2.setTotalAmount(new BigDecimal("4500"));
        order2.setBilledAmount(new BigDecimal("4000"));
        order2.setPointsUsed(new BigDecimal("300"));
        order2.setDiscounts(new BigDecimal("200"));
        order2.addItem(item2);
        order2.addItem(item3);

        // Order 3: No items, empty payments
        RakutenOrder order3 = new RakutenOrder("R-003", LocalDate.of(2023, 1, 20));
        order3.setTotalAmount(new BigDecimal("0"));
        order3.setBilledAmount(new BigDecimal("0"));
        order3.setPointsUsed(new BigDecimal("0"));
        order3.setDiscounts(new BigDecimal("0"));
        ReflectionTestUtils.setField(order3, "payments", new ArrayList<>());

        // Order 4: Null items and null payments
        RakutenOrder order4 = new RakutenOrder("R-004", LocalDate.of(2023, 1, 2));
        order4.setTotalAmount(new BigDecimal("500"));
        order4.setBilledAmount(new BigDecimal("500"));
        order4.setPointsUsed(new BigDecimal("0"));
        order4.setDiscounts(new BigDecimal("0"));
        ReflectionTestUtils.setField(order4, "items", null);
        ReflectionTestUtils.setField(order4, "payments", null);

        // Order 5: Long name
        RakutenOrder order5 = new RakutenOrder("R-005", LocalDate.of(2023, 1, 15));
        order5.setTotalAmount(new BigDecimal("100"));
        order5.setBilledAmount(new BigDecimal("100"));
        order5.setPointsUsed(new BigDecimal("0"));
        order5.setDiscounts(new BigDecimal("0"));
        order5.addItem(item4);

        List<RakutenOrder> orders = new ArrayList<>(List.of(order1, order2, order3, order4, order5));
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
        assertTrue(reportAsc.contains("(https://item.rakuten.co.jp/shop-abc/item-123/)")); // link for item1
        assertTrue(reportAsc.contains("- Item | name (2000 JPY x 2)")); // item2 has no link, full name in details
        assertTrue(reportAsc.contains("**Payment Method:** Credit Card"));
        assertTrue(reportAsc.contains("- **Total Saved (Points/Discounts):** 500 JPY"));

        // --- Test DATE_DESC ---
        StringBuilder sbDesc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.DATE_DESC, "testUser", sbDesc);
        String reportDesc = sbDesc.toString();
        assertTrue(reportDesc.indexOf("R-003") < reportDesc.indexOf("R-001"));

        // --- Test AMOUNT_ASC ---
        StringBuilder sbAmtAsc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.AMOUNT_ASC, "testUser", sbAmtAsc);
        String reportAmtAsc = sbAmtAsc.toString();
        assertTrue(reportAmtAsc.indexOf("R-003") < reportAmtAsc.indexOf("R-004"));
        assertTrue(reportAmtAsc.indexOf("R-004") < reportAmtAsc.indexOf("R-001"));
        assertTrue(reportAmtAsc.indexOf("R-001") < reportAmtAsc.indexOf("R-002"));

        // --- Test AMOUNT_DESC ---
        StringBuilder sbAmtDesc = new StringBuilder();
        strategy.generateReportSection(dateRange, ReportSortOption.AMOUNT_DESC, "testUser", sbAmtDesc);
        String reportAmtDesc = sbAmtDesc.toString();
        assertTrue(reportAmtDesc.indexOf("R-002") < reportAmtDesc.indexOf("R-001"));
        assertTrue(reportAmtDesc.indexOf("R-001") < reportAmtDesc.indexOf("R-004"));
        assertTrue(reportAmtDesc.indexOf("R-004") < reportAmtDesc.indexOf("R-003"));
    }
}
