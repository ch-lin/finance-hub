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
package ch.lin.finance.hub.backend.api.domain.model.accounting.salary;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SalaryRecordTest {

    @Test
    void testConstructorAndSetters() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        SalaryRecord record = new SalaryRecord(date);

        assertEquals(date, record.getPaymentDate());
        
        // Defaults
        assertEquals(0L, record.getGrossAmount());
        assertEquals(0L, record.getNetAmount());
        
        // Test Setters
        record.setGrossAmount(500000L);
        record.setNetAmount(400000L);
        record.setBaseSalary(300000L);
        record.setOvertimePay(50000L);
        record.setCommission(10000L);
        record.setFitnessAllowance(5000L);
        record.setHealthInsurance(20000L);
        record.setPension(40000L);
        record.setEmploymentInsurance(2000L);
        record.setIncomeTax(15000L);
        record.setResidentTax(10000L);
        record.setEsppContribution(8000L);
        record.setNote("January Salary");

        assertEquals(500000L, record.getGrossAmount());
        assertEquals(400000L, record.getNetAmount());
        assertEquals(300000L, record.getBaseSalary());
        assertEquals(50000L, record.getOvertimePay());
        assertEquals(10000L, record.getCommission());
        assertEquals(5000L, record.getFitnessAllowance());
        assertEquals(20000L, record.getHealthInsurance());
        assertEquals(40000L, record.getPension());
        assertEquals(2000L, record.getEmploymentInsurance());
        assertEquals(15000L, record.getIncomeTax());
        assertEquals(10000L, record.getResidentTax());
        assertEquals(8000L, record.getEsppContribution());
        assertEquals("January Salary", record.getNote());
        
        // Test Details
        Map<String, Long> details = new HashMap<>();
        details.put("customBonus", 100000L);
        record.setDetails(details);
        
        assertEquals(1, record.getDetails().size());
        assertEquals(100000L, record.getDetails().get("customBonus"));
    }
    
    @Test
    void testEqualsAndHashCode() {
        LocalDate date1 = LocalDate.of(2025, 1, 15);
        LocalDate date2 = LocalDate.of(2025, 2, 15);
        
        SalaryRecord r1 = new SalaryRecord(date1);
        SalaryRecord r2 = new SalaryRecord(date1);
        SalaryRecord r3 = new SalaryRecord(date2);
        
        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1.hashCode(), r3.hashCode());
    }
}
