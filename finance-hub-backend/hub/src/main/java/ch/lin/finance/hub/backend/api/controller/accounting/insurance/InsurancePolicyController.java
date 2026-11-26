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
package ch.lin.finance.hub.backend.api.controller.accounting.insurance;

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

import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.InsurancePolicyService;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.insurance.dto.InsurancePolicyMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.insurance.dto.InsurancePolicyRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.insurance.dto.InsurancePolicyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/insurance-policies")
@RequiredArgsConstructor
public class InsurancePolicyController {

    private final InsurancePolicyService insurancePolicyService;
    private final InsurancePolicyMapper insurancePolicyMapper;

    @GetMapping
    public List<InsurancePolicyResponse> getAllPolicies() {
        return insurancePolicyService.getAllPolicies().stream()
                .map(result -> insurancePolicyMapper.toMaskedResponse(result, SecurityUtil.getCurrentUserId()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public InsurancePolicyResponse getPolicy(@PathVariable Long id) {
        return insurancePolicyMapper.toMaskedResponse(
                insurancePolicyService.getPolicy(id), SecurityUtil.getCurrentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InsurancePolicyResponse createPolicy(@Valid @RequestBody InsurancePolicyRequest request) {
        return insurancePolicyMapper.toMaskedResponse(
                insurancePolicyService.createPolicy(insurancePolicyMapper.toCommand(request)),
                SecurityUtil.getCurrentUserId());
    }

    @PutMapping("/{id}")
    public InsurancePolicyResponse updatePolicy(@PathVariable Long id,
            @Valid @RequestBody InsurancePolicyRequest request) {
        return insurancePolicyMapper.toMaskedResponse(
                insurancePolicyService.updatePolicy(id, insurancePolicyMapper.toCommand(request)),
                SecurityUtil.getCurrentUserId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolicy(@PathVariable Long id) {
        insurancePolicyService.deletePolicy(id);
    }
}
