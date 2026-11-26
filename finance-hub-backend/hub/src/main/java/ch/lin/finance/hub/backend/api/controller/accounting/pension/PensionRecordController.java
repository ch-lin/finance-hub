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
package ch.lin.finance.hub.backend.api.controller.accounting.pension;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.pension.PensionProjectionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.PensionRecordService;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.command.CreatePensionRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionRecordResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.pension.dto.PensionProjectionYearlyResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.pension.dto.PensionRecordMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.pension.dto.PensionRecordRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.pension.dto.PensionRecordResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for managing pension records.
 */
@RestController
@RequestMapping("/assets/pensions")
@RequiredArgsConstructor
public class PensionRecordController {

    /** Service for managing pension records. */
    private final PensionRecordService pensionRecordService;

    /** Service for projecting future pensions. */
    private final PensionProjectionService pensionProjectionService;

    /** Mapper for pension records. */
    private final PensionRecordMapper pensionRecordMapper;

    /**
     * Calculates future pension projections.
     * 
     * @param maxYears The maximum number of additional working years to project
     *                 (default 40).
     * @return A list of {@link PensionProjectionYearlyResponse}.
     */
    @GetMapping("/projection")
    public ResponseEntity<List<PensionProjectionYearlyResponse>> getProjection(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "maxYears", defaultValue = "40") int maxYears,
            @RequestParam(required = false) Integer pensionStartAgeOverride) {
        String userId = jwt.getSubject();
        List<PensionProjectionYearlyResponse> response = pensionProjectionService.calculateProjection(userId, maxYears,
                pensionStartAgeOverride).stream()
                .map(result -> pensionRecordMapper.toMaskedResponse(result, SecurityUtil.getCurrentUserId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all pension records.
     *
     * @return A list of all pension records.
     */
    @GetMapping
    public List<PensionRecordResponse> getAllRecords() {
        return pensionRecordService.getAllRecords().stream()
                .map(result -> pensionRecordMapper.toResponse(result, SecurityUtil.getCurrentUserId()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a new pension record or updates an existing one.
     *
     * @param request The pension record data to save.
     * @return The saved pension record.
     */
    @PostMapping
    public PensionRecordResponse createOrUpdateRecord(@Valid @RequestBody PensionRecordRequest request) {
        CreatePensionRecordCommand command = pensionRecordMapper.toCommand(request);
        PensionRecordResult created = pensionRecordService.createOrUpdateRecord(Objects.requireNonNull(command));
        return pensionRecordMapper.toResponse(created, SecurityUtil.getCurrentUserId());
    }

    /**
     * Deletes a pension record by its ID.
     *
     * @param id The ID of the pension record to delete.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@org.springframework.lang.NonNull @PathVariable Long id) {
        pensionRecordService.deleteRecord(id);
        return ResponseEntity.ok().build();
    }
}
