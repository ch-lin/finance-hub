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
package ch.lin.finance.hub.backend.api.controller.accounting.salary;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.salary.SalaryRecordService;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.command.SaveSalaryRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.result.SalaryRecordResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.salary.dto.SalaryRecordMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.salary.dto.SalaryRecordRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.salary.dto.SalaryRecordResponse;
import jakarta.validation.Valid;

/**
 * REST controller for managing salary records.
 */
@RestController
@RequestMapping("/salaries")
public class SalaryRecordController {

    /** Service for managing salary records. */
    private final SalaryRecordService salaryRecordService;

    private final SalaryRecordMapper salaryRecordMapper;

    /**
     * Constructs a new SalaryRecordController.
     *
     * @param salaryRecordService The service for salary records.
     * @param salaryRecordMapper  The mapper for salary records.
     */
    public SalaryRecordController(SalaryRecordService salaryRecordService,
            SalaryRecordMapper salaryRecordMapper) {
        this.salaryRecordService = salaryRecordService;
        this.salaryRecordMapper = salaryRecordMapper;
    }

    /**
     * Retrieves all salary records sorted by payment date in descending order.
     *
     * @return A list of all salary records.
     */
    @GetMapping
    public List<SalaryRecordResponse> getAllRecords() {
        return salaryRecordService.getAllRecords().stream()
                .map(result -> salaryRecordMapper.toResponse(result, SecurityUtil.getCurrentUserId()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a new salary record.
     *
     * @param request The salary record details.
     * @return The created salary record.
     */
    @PostMapping
    public SalaryRecordResponse createRecord(
            @Valid @RequestBody SalaryRecordRequest request) {
        SaveSalaryRecordCommand command = salaryRecordMapper
                .toCommand(request);
        ch.lin.finance.hub.backend.api.app.service.accounting.salary.result.SalaryRecordResult result = salaryRecordService
                .createRecord(command);
        return salaryRecordMapper.toResponse(result, SecurityUtil.getCurrentUserId());
    }

    /**
     * Updates an existing salary record.
     *
     * @param id      The ID of the salary record to update.
     * @param request The updated salary record details.
     * @return The updated salary record.
     */
    @PutMapping("/{id}")
    public SalaryRecordResponse updateRecord(
            @PathVariable Long id,
            @Valid @RequestBody SalaryRecordRequest request) {
        SaveSalaryRecordCommand command = salaryRecordMapper.toCommand(request);
        SalaryRecordResult result = salaryRecordService.updateRecord(id, command);
        return salaryRecordMapper.toResponse(result, SecurityUtil.getCurrentUserId());
    }
}
