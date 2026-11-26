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
package ch.lin.finance.hub.backend.api.domain.model.accounting.insurance;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InsurancePolicyTest {

    @Test
    void testConstructorRequiresNonNullFields() {
        assertThrows(NullPointerException.class,
                () -> new InsurancePolicy(null, InsurancePolicyType.LIFE, "POL-001", "生命保険", 50000L, null));

        assertThrows(NullPointerException.class,
                () -> new InsurancePolicy("ソニー生命保険株式会社", null, "POL-001", "生命保険", 50000L, null));

        assertThrows(NullPointerException.class,
                () -> new InsurancePolicy("ソニー生命保険株式会社", InsurancePolicyType.LIFE, null, "生命保険", 50000L, null));

        assertThrows(NullPointerException.class,
                () -> new InsurancePolicy("ソニー生命保険株式会社", InsurancePolicyType.LIFE, "POL-001", null, 50000L, null));
    }

    @Test
    void testConstructorSuccess() {
        LocalDate endDate = LocalDate.of(2050, 12, 31);
        InsurancePolicy policy = new InsurancePolicy(
                "ソニー生命保険株式会社",
                InsurancePolicyType.LIFE,
                "POL-12345",
                "終身年金保険",
                120000L,
                endDate);

        assertEquals("ソニー生命保険株式会社", policy.getCompanyName());
        assertEquals(InsurancePolicyType.LIFE, policy.getPolicyType());
        assertEquals("POL-12345", policy.getPolicyNumber());
        assertEquals("終身年金保険", policy.getPolicyName());
        assertEquals(120000L, policy.getAnnualPremium());
        assertEquals(endDate, policy.getEndDate());
    }

    @Test
    void testMergeWithNullDoesNothing() {
        InsurancePolicy policy = new InsurancePolicy(
                "ソニー生命保険株式会社",
                InsurancePolicyType.LIFE,
                "POL-12345",
                "終身年金保険",
                120000L,
                LocalDate.of(2050, 12, 31));

        policy.merge(null);

        assertEquals("ソニー生命保険株式会社", policy.getCompanyName());
        assertEquals(InsurancePolicyType.LIFE, policy.getPolicyType());
        assertEquals("POL-12345", policy.getPolicyNumber());
        assertEquals("終身年金保険", policy.getPolicyName());
        assertEquals(120000L, policy.getAnnualPremium());
        assertEquals(LocalDate.of(2050, 12, 31), policy.getEndDate());
    }

    @Test
    void testMergeUnconditionalOverwrite() {
        InsurancePolicy original = new InsurancePolicy(
                "ソニー生命保険株式会社",
                InsurancePolicyType.LIFE,
                "POL-12345",
                "定期保険",
                80000L,
                LocalDate.of(2035, 6, 30));

        InsurancePolicy updated = new InsurancePolicy(
                "ソニー損害保険株式会社",
                InsurancePolicyType.FIRE,
                "FIRE-999",
                "火災保険 (一時払)",
                0L,
                null);

        original.merge(updated);

        assertEquals("ソニー損害保険株式会社", original.getCompanyName());
        assertEquals(InsurancePolicyType.FIRE, original.getPolicyType());
        assertEquals("FIRE-999", original.getPolicyNumber());
        assertEquals("火災保険 (一時払)", original.getPolicyName());
        assertEquals(0L, original.getAnnualPremium(), "0L premium should unconditionally overwrite");
        assertNull(original.getEndDate(), "null endDate should unconditionally overwrite");
    }
}
