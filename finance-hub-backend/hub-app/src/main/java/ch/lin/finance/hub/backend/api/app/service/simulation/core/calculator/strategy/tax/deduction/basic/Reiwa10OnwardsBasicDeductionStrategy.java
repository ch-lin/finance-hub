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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic;

/**
 * Strategy implementation for Reiwa 10 and onwards tax years (>= 2028).
 * Permanent baseline brackets per NTA No.1199 (990,000 JPY for low income,
 * 620,000 JPY standard).
 */
public class Reiwa10OnwardsBasicDeductionStrategy implements BasicDeductionStrategy {

    @Override
    public long calculateIncomeTaxDeduction(long totalIncome) {
        if (totalIncome <= 1_320_000L) {
            return 990_000L;
        } else if (totalIncome <= 23_500_000L) {
            return 620_000L;
        } else if (totalIncome <= 24_000_000L) {
            return 480_000L;
        } else if (totalIncome <= 24_500_000L) {
            return 320_000L;
        } else if (totalIncome <= 25_000_000L) {
            return 160_000L;
        } else {
            return 0L;
        }
    }

    @Override
    public long calculateInhabitantTaxDeduction(long totalIncome) {
        if (totalIncome <= 24_000_000L) {
            return 430_000L;
        } else if (totalIncome <= 24_500_000L) {
            return 290_000L;
        } else if (totalIncome <= 25_000_000L) {
            return 150_000L;
        } else {
            return 0L;
        }
    }
}
