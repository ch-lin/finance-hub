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
package ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MortgageRecordTest {

    @Test
    void testMerge_Overwrites14FactFieldsUnconditionally_ZeroOverProtection() {
        // Arrange existing record with initial facts
        List<BonusPayment> existingBonuses = new ArrayList<>();
        existingBonuses.add(new BonusPayment(6, 300_000L));
        existingBonuses.add(new BonusPayment(12, 300_000L));

        MortgageRecord existing = new MortgageRecord(
                "ソニー銀行", "SN-001", "変動金利住宅ローン", 50_000_000L, 48_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 4, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, existingBonuses
        );

        // Incoming facts with new balance, rate, payment, and bonus updates
        List<BonusPayment> incomingBonuses = new ArrayList<>();
        incomingBonuses.add(new BonusPayment(7, 350_000L));

        MortgageRecord incoming = new MortgageRecord(
                "ソニー銀行", "SN-001", "変動金利住宅ローン（改定）", 50_000_000L, 45_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 10, 1),
                420, 0.0065, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                132_000L, incomingBonuses
        );

        // Act
        existing.merge(incoming);

        // Assert: All 14 facts are updated directly without any null/old-value protection
        assertEquals("変動金利住宅ローン（改定）", existing.getLoanName());
        assertEquals(45_000_000L, existing.getCurrentBalance());
        assertEquals(LocalDate.of(2025, 10, 1), existing.getBalanceRecordDate());
        assertEquals(0.0065, existing.getInterestRate(), 1e-6);
        assertEquals(132_000L, existing.getMonthlyPayment());
        assertEquals(1, existing.getBonusPayments().size());
        assertEquals(7, existing.getBonusPayments().get(0).getMonth());
        assertEquals(350_000L, existing.getBonusPayments().get(0).getAmount());
    }

    @Test
    void testMerge_StrictlyPreservesUserManagedFields() {
        // Arrange existing record with user custom configurations (tax deduction, early repayments, etc.)
        MortgageRecord existing = new MortgageRecord(
                "ソニー銀行", "SN-001", "変動金利住宅ローン", 50_000_000L, 48_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 4, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );

        // User custom settings
        existing.setOccupancyDate(LocalDate.of(2025, 5, 1));
        existing.setResidenceType(ResidenceType.GENERAL);
        existing.setPropertyType(PropertyType.NEW_DETACHED_HOUSE);
        existing.setTaxDeductionEligible(true);
        existing.setTaxDeductionEndYear(2038);
        existing.setTaxDeductionRate(0.007);
        existing.setMaxTaxDeductionAmount(210_000L);
        existing.setHasFiveYearRule(false);

        MortgageEarlyRepayment earlyRepayment = new MortgageEarlyRepayment(
                existing, LocalDate.of(2025, 6, 1), 1_000_000L,
                EarlyRepaymentType.REDUCE_TERM, 0L, 10
        );
        existing.getEarlyRepayments().add(earlyRepayment);

        // Incoming record produced by parser has null/defaults for user-managed fields
        MortgageRecord incoming = new MortgageRecord(
                "ソニー銀行", "SN-001", "変動金利住宅ローン", 50_000_000L, 47_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 6, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );

        // Act
        existing.merge(incoming);

        // Assert: Facts updated
        assertEquals(47_000_000L, existing.getCurrentBalance());
        assertEquals(LocalDate.of(2025, 6, 1), existing.getBalanceRecordDate());

        // Assert: All 9 user-managed fields strictly preserved (never overwritten or cleared)
        assertEquals(LocalDate.of(2025, 5, 1), existing.getOccupancyDate());
        assertEquals(ResidenceType.GENERAL, existing.getResidenceType());
        assertEquals(PropertyType.NEW_DETACHED_HOUSE, existing.getPropertyType());
        assertTrue(existing.getTaxDeductionEligible());
        assertEquals(2038, existing.getTaxDeductionEndYear());
        assertEquals(0.007, existing.getTaxDeductionRate(), 1e-6);
        assertEquals(210_000L, existing.getMaxTaxDeductionAmount());
        assertFalse(existing.getHasFiveYearRule());
        assertEquals(1, existing.getEarlyRepayments().size());
        assertEquals(1_000_000L, existing.getEarlyRepayments().get(0).getAmount());
    }

    @Test
    void testMerge_NullIncoming_DoesNothing() {
        MortgageRecord existing = new MortgageRecord(
                "ソニー銀行", "SN-001", "変動金利住宅ローン", 50_000_000L, 48_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 4, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );

        existing.merge(null);

        assertEquals(48_000_000L, existing.getCurrentBalance());
    }
}
