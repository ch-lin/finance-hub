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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary.result;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Result object for returning salary records from the service layer.
 * Contains both the core salary data and base privacy fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SalaryRecordResult extends BaseResult {
    private Long id;
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
    private Map<String, Long> details = new HashMap<>();

    /**
     * Converts a SalaryRecord domain entity to a SalaryRecordResult.
     * Extracts privacy attributes into the base class.
     *
     * @param entity the SalaryRecord entity
     * @return the mapped SalaryRecordResult
     */
    public static SalaryRecordResult fromEntity(SalaryRecord entity) {
        if (entity == null) {
            return null;
        }

        SalaryRecordResult result = new SalaryRecordResult();

        // Base Result fields for masking/privacy
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());

        // Standard properties
        result.setId(entity.getId());
        result.setType(entity.getType());
        result.setPaymentDate(entity.getPaymentDate());
        result.setGrossAmount(entity.getGrossAmount());
        result.setNetAmount(entity.getNetAmount());
        result.setBaseSalary(entity.getBaseSalary());
        result.setOvertimePay(entity.getOvertimePay());
        result.setCommission(entity.getCommission());
        result.setFitnessAllowance(entity.getFitnessAllowance());
        result.setHealthInsurance(entity.getHealthInsurance());
        result.setPension(entity.getPension());
        result.setEmploymentInsurance(entity.getEmploymentInsurance());
        result.setIncomeTax(entity.getIncomeTax());
        result.setResidentTax(entity.getResidentTax());
        result.setEsppContribution(entity.getEsppContribution());
        result.setNote(entity.getNote());

        // Deep copy of details map to prevent accidental mutation of the entity,
        // or just reference copy if read-only is guaranteed. Reference copy is fine for
        // DTOs.
        if (entity.getDetails() != null) {
            result.setDetails(new HashMap<>(entity.getDetails()));
        }

        return result;
    }
}
