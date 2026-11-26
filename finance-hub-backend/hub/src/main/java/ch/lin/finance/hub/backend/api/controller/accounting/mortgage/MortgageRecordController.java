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
package ch.lin.finance.hub.backend.api.controller.accounting.mortgage;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.MortgageRecordService;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.mortgage.dto.MortgageRecordMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.mortgage.dto.MortgageRecordRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.mortgage.dto.MortgageRecordResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller for managing mortgage records.
 */
@RestController
@RequestMapping("/mortgages")
@RequiredArgsConstructor
public class MortgageRecordController {

    private final MortgageRecordService mortgageRecordService;
    private final MortgageRecordMapper mortgageRecordMapper;

    @GetMapping
    public List<MortgageRecordResponse> getAllMortgages() {
        return mortgageRecordService.getAllMortgages().stream()
                .map(result -> mortgageRecordMapper.toMaskedResponse(result, SecurityUtil.getCurrentUserId()))
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MortgageRecordResponse createMortgage(@Valid @RequestBody MortgageRecordRequest request) {
        return mortgageRecordMapper
                .toMaskedResponse(mortgageRecordService.createMortgage(mortgageRecordMapper.toCommand(request)), SecurityUtil.getCurrentUserId());
    }

    @PutMapping("/{id}")
    public MortgageRecordResponse updateMortgage(
            @PathVariable Long id,
            @Valid @RequestBody MortgageRecordRequest request) {
        return mortgageRecordMapper
                .toMaskedResponse(mortgageRecordService.updateMortgage(id, mortgageRecordMapper.toCommand(request)), SecurityUtil.getCurrentUserId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMortgage(@PathVariable Long id) {
        mortgageRecordService.deleteMortgage(id);
    }
}
