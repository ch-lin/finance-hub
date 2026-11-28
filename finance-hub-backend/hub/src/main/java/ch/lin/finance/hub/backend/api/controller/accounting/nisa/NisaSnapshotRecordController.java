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
package ch.lin.finance.hub.backend.api.controller.accounting.nisa;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaProjectionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaSnapshotRecordService;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.UpdateNisaReturnRateCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaSnapshotRecordResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaProjectionYearlyResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaSnapshotMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaSnapshotRecordResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.UpdateNisaReturnRateRequest;
import jakarta.validation.Valid;

/**
 * REST controller for managing NISA snapshot records.
 */
@RestController
@RequestMapping("/assets/nisa")
public class NisaSnapshotRecordController {

    private final NisaSnapshotRecordService nisaSnapshotRecordService;
    private final NisaProjectionService nisaProjectionService;
    private final NisaSnapshotMapper nisaSnapshotMapper;

    /**
     * Constructs a new NisaSnapshotRecordController.
     *
     * @param nisaSnapshotRecordService The service for NISA snapshot records.
     * @param nisaProjectionService     The service for calculating future
     *                                  projections.
     * @param nisaSnapshotMapper        The mapper for NISA records.
     */
    public NisaSnapshotRecordController(NisaSnapshotRecordService nisaSnapshotRecordService,
            NisaProjectionService nisaProjectionService, NisaSnapshotMapper nisaSnapshotMapper) {
        this.nisaSnapshotRecordService = nisaSnapshotRecordService;
        this.nisaProjectionService = nisaProjectionService;
        this.nisaSnapshotMapper = nisaSnapshotMapper;
    }

    /**
     * Retrieves all NISA snapshot records sorted by record date in descending
     * order.
     *
     * @return A list of all NISA snapshot records.
     */
    @GetMapping
    public List<NisaSnapshotRecordResponse> getAllRecords() {
        return nisaSnapshotRecordService.getAllRecords().stream()
                .map(result -> nisaSnapshotMapper.toResponse(result, SecurityUtil.getCurrentUserId()))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the projected future value of the NISA portfolio.
     *
     * @param years The number of years to project (defaults to 30 if not
     *              specified).
     * @return A list of yearly projections.
     */
    @GetMapping("/projection")
    public List<NisaProjectionYearlyResponse> getProjection(
            @RequestParam(defaultValue = "30") int years) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        return nisaProjectionService.calculateProjection(currentUserId, years).stream()
                .map(result -> nisaSnapshotMapper.toMaskedResponse(result, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * Updates the expected return rate for a specific snapshot record.
     * 
     * @param id      The ID of the NISA snapshot record.
     * @param request The request containing the new return rate.
     * @return The updated record.
     */
    @PatchMapping("/{id}/return-rate")
    public NisaSnapshotRecordResponse updateReturnRate(@PathVariable @NonNull Long id,
            @Valid @RequestBody UpdateNisaReturnRateRequest request) {

        UpdateNisaReturnRateCommand command = nisaSnapshotMapper.toCommand(request);
        NisaSnapshotRecordResult updated = nisaSnapshotRecordService
                .updateReturnRate(id, command);
        return nisaSnapshotMapper.toResponse(updated, SecurityUtil.getCurrentUserId());
    }
}
