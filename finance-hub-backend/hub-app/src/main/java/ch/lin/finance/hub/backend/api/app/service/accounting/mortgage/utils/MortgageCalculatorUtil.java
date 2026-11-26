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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.utils;

import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.BonusPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;

public class MortgageCalculatorUtil {

    /**
     * Calculates the total number of months that the mortgage term has been reduced
     * by historical early repayments (REDUCE_TERM type) up to the specified
     * YearMonth.
     */
    public static int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) {
        if (m.getEarlyRepayments() == null) {
            return 0;
        }
        int total = 0;
        for (var er : m.getEarlyRepayments()) {
            if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_TERM) {
                YearMonth erYm = YearMonth.from(er.getRepaymentDate());
                if (!erYm.isAfter(currentYm)) {
                    Integer reduced = er.getReducedMonths();
                    total += (reduced != null) ? reduced : 0;
                }
            }
        }
        return total;
    }

    /**
     * Calculates the fixed component of the monthly payment (legacy signature).
     * Delegates to the bonus-aware version with 0 bonus.
     */
    public static long calculateFixedPaymentOrPrincipal(
            long currentBalance,
            double annualInterestRate,
            int remainingMonths,
            RepaymentMethod repaymentMethod) {
        return calculateFixedPaymentOrPrincipal(currentBalance, annualInterestRate, remainingMonths,
                repaymentMethod, Collections.emptyList(), 1);
    }

    public static long calculateFixedPaymentOrPrincipal(
            long currentBalance,
            double annualInterestRate,
            int remainingMonths,
            RepaymentMethod repaymentMethod,
            List<BonusPayment> bonusPayments,
            int currentMonthValue) {

        if (currentBalance <= 0 || remainingMonths <= 0) {
            return 0;
        }
        double r = annualInterestRate / 12.0;

        if (repaymentMethod == RepaymentMethod.EQUAL_PRINCIPAL) {
            long totalBonus = countRemainingBonusPaymentsAmount(remainingMonths, bonusPayments, currentMonthValue);
            long monthlyPortionBalance = Math.max(0, currentBalance - totalBonus);
            return monthlyPortionBalance / remainingMonths;
        } else {
            double bonusPV = presentValueOfBonusStream(r, remainingMonths, bonusPayments, currentMonthValue);
            long monthlyPortionBalance = Math.max(0, (long) (currentBalance - bonusPV));
            if (r == 0) {
                return monthlyPortionBalance / remainingMonths;
            }
            double mathPow = Math.pow(1 + r, remainingMonths);
            return Math.round(monthlyPortionBalance * r * mathPow / (mathPow - 1));
        }
    }

    private static long countRemainingBonusPaymentsAmount(int remainingMonths, List<BonusPayment> bonusPayments,
            int currentMonthValue) {
        if (bonusPayments == null || bonusPayments.isEmpty())
            return 0;
        long total = 0, month = currentMonthValue;
        for (int k = 1; k <= remainingMonths; k++) {
            for (BonusPayment bp : bonusPayments) {
                if (bp.getMonth() != null && bp.getMonth() == month) {
                    Long amount = bp.getAmount();
                    total += amount != null ? amount : 0L;
                }
            }
            month = (month == 12) ? 1 : month + 1;
        }
        return total;
    }

    private static double presentValueOfBonusStream(double monthlyRate, int remainingMonths,
            List<BonusPayment> bonusPayments, int currentMonthValue) {
        if (bonusPayments == null || bonusPayments.isEmpty())
            return 0.0;
        double pv = 0.0;
        long month = currentMonthValue;
        for (int k = 1; k <= remainingMonths; k++) {
            for (BonusPayment bp : bonusPayments) {
                if (bp.getMonth() != null && bp.getMonth() == month) {
                    Long rawAmt = bp.getAmount();
                    long amt = rawAmt != null ? rawAmt : 0L;
                    if (amt > 0) {
                        pv += amt / Math.pow(1 + monthlyRate, k);
                    }
                }
            }
            month = (month == 12) ? 1 : month + 1;
        }
        return pv;
    }

}
