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

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.tax.TaxConstants;

class CashflowLiquidationCalculatorTest {

    private CashflowLiquidationCalculator liquidationService;
    private SimulationContext context;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        liquidationService = new CashflowLiquidationCalculator();
        context = new SimulationContext();
    }

    @Test
    void testNoNegativeCashflow() {
        context.setCurrentLiquidCash(100_000L);
        context.setCompanyStockBalance(50_000L);
        context.setNisaBalance(1_000_000L);

        liquidationService.processNegativeCashflow(context);

        assertEquals(100_000L, context.getCurrentLiquidCash());
        assertEquals(50_000L, context.getCompanyStockBalance());
        assertEquals(1_000_000L, context.getNisaBalance());
    }

    @Test
    void testLiquidateCompanyStockOnly() {
        context.setCurrentLiquidCash(-40_000L);
        context.setCompanyStockBalance(50_000L);
        context.setNisaBalance(1_000_000L);

        liquidationService.processNegativeCashflow(context);

        assertEquals(0L, context.getCurrentLiquidCash());
        assertEquals(10_000L, context.getCompanyStockBalance());
        assertEquals(1_000_000L, context.getNisaBalance());
    }

    @Test
    void testLiquidateCompanyStockAndNisa() {
        context.setCurrentLiquidCash(-100_000L);
        context.setCompanyStockBalance(40_000L);
        context.setNisaBalance(1_000_000L);
        context.setAccumulatedNisaPrincipal(800_000L); // 80% cost ratio

        liquidationService.processNegativeCashflow(context);

        assertEquals(0L, context.getCurrentLiquidCash());
        assertEquals(0L, context.getCompanyStockBalance());
        assertEquals(940_000L, context.getNisaBalance());

        // Sold 60k NISA. Cost ratio is 80%. Principal to recover = 60k * 0.8 = 48k
        assertEquals(48_000L, context.getNisaPrincipalToRecoverNextYear());
    }

    @Test
    void testBorrowingWhenAllAssetsDepleted() {
        context.setCurrentLiquidCash(-500_000L);
        context.setCompanyStockBalance(50_000L);
        context.setNisaBalance(100_000L);
        context.setAccumulatedNisaPrincipal(80_000L);

        liquidationService.processNegativeCashflow(context);

        // Required to sell: 500k.
        // Sold 50k stock -> remaining deficit 450k.
        // Sold 100k NISA -> remaining deficit 350k.
        // 350k borrowed. Interest = 350k * 0.14 = 49k.
        // Total negative cash = -350k - 49k = -399k
        long expectedBorrowing = 350_000L;
        long interest = Math.round(expectedBorrowing * TaxConstants.NEGATIVE_CASH_BORROWING_RATE);

        assertEquals(-(expectedBorrowing + interest), context.getCurrentLiquidCash());
        assertEquals(0L, context.getCompanyStockBalance());
        assertEquals(0L, context.getNisaBalance());
        assertEquals(80_000L, context.getNisaPrincipalToRecoverNextYear());
    }
}
