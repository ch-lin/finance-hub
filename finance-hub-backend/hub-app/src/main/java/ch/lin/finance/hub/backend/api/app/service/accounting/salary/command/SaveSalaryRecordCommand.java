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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary.command;

import java.time.LocalDate;
import java.util.Map;

import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveSalaryRecordCommand {

    private SalaryType type;
    private LocalDate paymentDate;
    private Long grossAmount;
    private Long netAmount;
    private Long baseSalary;
    private Long overtimePay;
    private Long commission;
    private Long fitnessAllowance;
    private Long healthInsurance;
    private Long pension;
    private Long employmentInsurance;
    private Long incomeTax;
    private Long residentTax;
    private Long esppContribution;
    private String note;
    private Map<String, Long> details;
}
