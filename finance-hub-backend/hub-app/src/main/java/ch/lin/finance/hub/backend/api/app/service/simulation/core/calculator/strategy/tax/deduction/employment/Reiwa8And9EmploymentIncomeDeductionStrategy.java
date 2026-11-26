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
 * Strategy implementation for Reiwa 8 and 9 tax years (2026-2027, 令和8～9年分).
 * Statutory basis: Act on Special Measures Concerning Taxation art 29-4 para 1
 * (temporary special expansion, minimum guarantee 740,000 JPY, 2.2M smooth connection, cap 1,950,000 JPY).
 */
public class Reiwa8And9EmploymentIncomeDeductionStrategy implements EmploymentIncomeDeductionStrategy {

    @Override
    public long calculateDeduction(long grossSalary) {
        if (grossSalary <= 0) {
            return 0L;
        }
        if (grossSalary <= 2_200_000L) {
            return 740_000L;
        } else if (grossSalary <= 3_600_000L) {
            return (long) (grossSalary * 0.3) + 80_000L;
        } else if (grossSalary <= 6_600_000L) {
            return (long) (grossSalary * 0.2) + 440_000L;
        } else if (grossSalary <= 8_500_000L) {
            return (long) (grossSalary * 0.1) + 1_100_000L;
        } else {
            return 1_950_000L;
        }
    }
}
