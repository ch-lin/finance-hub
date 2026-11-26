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
package ch.lin.finance.hub.backend.api.controller.accounting.salary.dto;

import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryRecordRequest {

    @NotNull(message = "Salary type is required")
    private SalaryType type;

    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    @Min(value = 0, message = "Gross amount cannot be negative")
    private Long grossAmount;

    @Min(value = 0, message = "Net amount cannot be negative")
    private Long netAmount;

    @Min(value = 0, message = "Base salary cannot be negative")
    private Long baseSalary;

    @Min(value = 0, message = "Overtime pay cannot be negative")
    private Long overtimePay;

    @Min(value = 0, message = "Commission cannot be negative")
    private Long commission;

    @Min(value = 0, message = "Fitness allowance cannot be negative")
    private Long fitnessAllowance;

    @Min(value = 0, message = "Health insurance cannot be negative")
    private Long healthInsurance;

    @Min(value = 0, message = "Pension cannot be negative")
    private Long pension;

    @Min(value = 0, message = "Employment insurance cannot be negative")
    private Long employmentInsurance;

    @Min(value = 0, message = "Income tax cannot be negative")
    private Long incomeTax;

    @Min(value = 0, message = "Resident tax cannot be negative")
    private Long residentTax;

    @Min(value = 0, message = "ESPP contribution cannot be negative")
    private Long esppContribution;

    private String note;

    private Map<String, Long> details;
}
