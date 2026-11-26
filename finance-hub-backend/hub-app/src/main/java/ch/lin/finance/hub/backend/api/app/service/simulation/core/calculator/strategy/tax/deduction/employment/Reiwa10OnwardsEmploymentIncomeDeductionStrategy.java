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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.employment;

/**
 * Strategy implementation for Reiwa 10 onwards tax years (>= 2028, 令和10年分以降).
 * Statutory basis: Income Tax Act art 28 para 3 item 1 (permanent baseline amended
 * by Reiwa 8 Act No. 12, temporary 740,000 JPY sunsetting back to 690,000 JPY
 * with Math.max protection).
 */
public class Reiwa10OnwardsEmploymentIncomeDeductionStrategy implements EmploymentIncomeDeductionStrategy {

    @Override
    public long calculateDeduction(long grossSalary) {
        if (grossSalary <= 0) {
            return 0L;
        }
        if (grossSalary <= 3_600_000L) {
            return Math.max(690_000L, (long) (grossSalary * 0.3) + 80_000L);
        } else if (grossSalary <= 6_600_000L) {
            return (long) (grossSalary * 0.2) + 440_000L;
        } else if (grossSalary <= 8_500_000L) {
            return (long) (grossSalary * 0.1) + 1_100_000L;
        } else {
            return 1_950_000L;
        }
    }
}
