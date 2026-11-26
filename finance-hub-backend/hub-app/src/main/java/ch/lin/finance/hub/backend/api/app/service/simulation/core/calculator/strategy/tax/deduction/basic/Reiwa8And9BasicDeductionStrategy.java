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
 * Strategy implementation for Reiwa 8 and 9 tax years (2026-2027). Temporary
 * special expansion per NTA No.1199 (1,040,000 JPY for income up to 4.89M JPY).
 */
public class Reiwa8And9BasicDeductionStrategy implements BasicDeductionStrategy {

    @Override
    public long calculateIncomeTaxDeduction(long totalIncome) {
        if (totalIncome <= 4_890_000L) {
            return 1_040_000L;
        } else if (totalIncome <= 6_550_000L) {
            return 670_000L;
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
