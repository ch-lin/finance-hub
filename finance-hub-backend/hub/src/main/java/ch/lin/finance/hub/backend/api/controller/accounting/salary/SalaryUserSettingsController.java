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

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.salary.SalaryUserSettingsService;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.command.SaveSalaryUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.result.SalaryUserSettingsResult;
import ch.lin.finance.hub.backend.api.controller.accounting.salary.dto.SalaryUserSettingsMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.salary.dto.SalaryUserSettingsRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.salary.dto.SalaryUserSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/salaries/user-settings")
@RequiredArgsConstructor
@Tag(name = "Salary Configuration")
public class SalaryUserSettingsController {

    private final SalaryUserSettingsService salaryUserSettingsService;
    private final SalaryUserSettingsMapper mapper;

    @Operation(summary = "Get Salary User Settings")
    @GetMapping
    public ResponseEntity<SalaryUserSettingsResponse> getSettings() {
        return salaryUserSettingsService.getSettings()
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Update Salary User Settings")
    @PutMapping
    public ResponseEntity<SalaryUserSettingsResponse> updateSettings(
            @Valid @RequestBody SalaryUserSettingsRequest request) {
        SaveSalaryUserSettingsCommand command = mapper.toCommand(request);
        SalaryUserSettingsResult result = salaryUserSettingsService.saveSettings(Objects.requireNonNull(command));
        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
