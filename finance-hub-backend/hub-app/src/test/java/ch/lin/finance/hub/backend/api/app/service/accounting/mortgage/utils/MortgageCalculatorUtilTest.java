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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.BonusPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;

class MortgageCalculatorUtilTest {

    @Test
    void bonusAwarePayment_fullyAmortizesExactlyOverLoanTerm() {
        long balance = 30_000_000L;
        double rate = 0.005;
        int remainingMonths = 420;
        long bonusAmount = 300_000L;
        int bonusMonth1 = 6, bonusMonth2 = 12, startMonth = 1;

        List<BonusPayment> bonusPayments = List.of(
                new BonusPayment(bonusMonth1, bonusAmount),
                new BonusPayment(bonusMonth2, bonusAmount));

        long monthlyPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
                balance, rate, remainingMonths, RepaymentMethod.EQUAL_PAYMENT,
                bonusPayments, startMonth);

        double simulatedBalance = balance;
        double monthlyRate = rate / 12.0;
        int month = startMonth;
        for (int k = 1; k <= remainingMonths; k++) {
            double interest = simulatedBalance * monthlyRate;
            double payment = monthlyPayment + ((month == bonusMonth1 || month == bonusMonth2) ? bonusAmount : 0);
            simulatedBalance -= (payment - interest);
            month = (month == 12) ? 1 : month + 1;
        }

        // Allow some rounding error from month-to-month integer/double math
        assertThat(simulatedBalance).isCloseTo(0.0, within(remainingMonths * 1.0));
    }

    @Test
    void bonusAwarePrincipal_fullyAmortizesExactlyOverLoanTerm() {
        long balance = 30_000_000L;
        double rate = 0.005;
        int remainingMonths = 420;
        long bonusAmount = 300_000L;
        int bonusMonth1 = 6, bonusMonth2 = 12, startMonth = 1;

        List<BonusPayment> bonusPayments = List.of(
                new BonusPayment(bonusMonth1, bonusAmount),
                new BonusPayment(bonusMonth2, bonusAmount));

        long monthlyPrincipal = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
                balance, rate, remainingMonths, RepaymentMethod.EQUAL_PRINCIPAL,
                bonusPayments, startMonth);

        double simulatedBalance = balance;
        int month = startMonth;
        for (int k = 1; k <= remainingMonths; k++) {
            double principalPayment = monthlyPrincipal
                    + ((month == bonusMonth1 || month == bonusMonth2) ? bonusAmount : 0);
            simulatedBalance -= principalPayment;
            month = (month == 12) ? 1 : month + 1;
        }

        assertThat(simulatedBalance).isCloseTo(0.0, within(remainingMonths * 1.0));
    }

    @Test
    void noBonusPayment_matchesOldBehavior() {
        long balance = 30_000_000L;
        double rate = 0.005;
        int remainingMonths = 420;

        long oldPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
                balance, rate, remainingMonths, RepaymentMethod.EQUAL_PAYMENT);

        long newPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
                balance, rate, remainingMonths, RepaymentMethod.EQUAL_PAYMENT,
                Collections.emptyList(), 1);

        assertThat(newPayment).isEqualTo(oldPayment);
    }

    @Test
    void testCalculateAccumulatedReducedMonths() {
        var m = Mockito.mock(MortgageRecord.class);

        var er1 = Mockito.mock(MortgageEarlyRepayment.class);
        Mockito.when(er1.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_TERM);
        Mockito.when(er1.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2023, 5, 15));
        Mockito.when(er1.getReducedMonths()).thenReturn(10);

        var er2 = Mockito.mock(MortgageEarlyRepayment.class);
        Mockito.when(er2.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_TERM);
        Mockito.when(er2.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2024, 8, 20));
        Mockito.when(er2.getReducedMonths()).thenReturn(5);

        var er3 = Mockito.mock(MortgageEarlyRepayment.class);
        Mockito.when(er3.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_PAYMENT); // different type
        Mockito.when(er3.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2023, 6, 1));
        Mockito.when(er3.getReducedMonths()).thenReturn(20);

        var er4 = Mockito.mock(MortgageEarlyRepayment.class);
        Mockito.when(er4.getRepaymentType()).thenReturn(EarlyRepaymentType.REDUCE_TERM);
        Mockito.when(er4.getRepaymentDate()).thenReturn(java.time.LocalDate.of(2025, 1, 10)); // future
        Mockito.when(er4.getReducedMonths()).thenReturn(12);

        Mockito.when(m.getEarlyRepayments()).thenReturn(List.of(er1, er2, er3, er4));

        // Target: 2024-12. Should include er1 (2023-05) and er2 (2024-08). Should
        // ignore er3 (wrong type) and er4 (future).
        int result = MortgageCalculatorUtil.calculateAccumulatedReducedMonths(m, java.time.YearMonth.of(2024, 12));

        assertThat(result).isEqualTo(15);
    }
}
