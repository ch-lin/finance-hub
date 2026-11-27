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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryRecordRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.command.SaveSalaryRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.result.SalaryRecordResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;

/**
 * Application service for managing salary records.
 */
@Service
public class SalaryRecordServiceImpl implements SalaryRecordService {

    private final SalaryRecordRepository salaryRecordRepository;
    private final OwnershipGuard ownershipGuard;

    public SalaryRecordServiceImpl(SalaryRecordRepository salaryRecordRepository, OwnershipGuard ownershipGuard) {
        this.salaryRecordRepository = salaryRecordRepository;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * Retrieves all salary records sorted by payment date in descending order.
     *
     * @return A list of all salary records.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalaryRecordResult> getAllRecords() {
        return salaryRecordRepository.findAllByOrderByPaymentDateDesc().stream()
                .map(SalaryRecordResult::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SalaryRecordResult createRecord(SaveSalaryRecordCommand command) {
        salaryRecordRepository.findByPaymentDate(command.getPaymentDate())
                .ifPresent(record -> {
                    throw new IllegalArgumentException(
                            "Salary record already exists for payment date: " + command.getPaymentDate());
                });

        SalaryRecord record = new SalaryRecord(command.getPaymentDate());
        applyCommandToEntity(command, record);

        return SalaryRecordResult.fromEntity(salaryRecordRepository.save(record));
    }

    @Override
    @Transactional
    public SalaryRecordResult updateRecord(Long id, SaveSalaryRecordCommand command) {
        SalaryRecord record = ownershipGuard.loadOwned(salaryRecordRepository, id);

        if (!record.getPaymentDate().equals(command.getPaymentDate())) {
            throw new IllegalArgumentException(
                    "Payment date cannot be modified on an existing record. Please delete and recreate the record.");
        }

        applyCommandToEntity(command, record);
        return SalaryRecordResult.fromEntity(salaryRecordRepository.save(record));
    }

    private void applyCommandToEntity(SaveSalaryRecordCommand command, SalaryRecord record) {
        record.setType(command.getType());
        record.setGrossAmount(command.getGrossAmount());
        record.setNetAmount(command.getNetAmount());
        record.setBaseSalary(command.getBaseSalary());
        record.setOvertimePay(command.getOvertimePay());
        record.setCommission(command.getCommission());
        record.setFitnessAllowance(command.getFitnessAllowance());
        record.setHealthInsurance(command.getHealthInsurance());
        record.setPension(command.getPension());
        record.setEmploymentInsurance(command.getEmploymentInsurance());
        record.setIncomeTax(command.getIncomeTax());
        record.setResidentTax(command.getResidentTax());
        record.setEsppContribution(command.getEsppContribution());
        record.setNote(command.getNote());
        if (command.getDetails() != null) {
            record.setDetails(new java.util.HashMap<>(command.getDetails()));
        }

        // Let the domain entity self-validate and apply rules
        record.validateAndSanitize();
    }
}
