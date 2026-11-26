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
package ch.lin.finance.hub.backend.api.controller.accounting.dc;

import java.util.List;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionUserSettingsService;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDcUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DcUserSettingsMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DcUserSettingsRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DcUserSettingsResponse;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/assets/dc/user-settings")
@RequiredArgsConstructor
@Tag(name = "Defined Contribution Configuration")
public class DefinedContributionUserSettingsController {

    private final DefinedContributionUserSettingsService dcUserSettingsService;
    private final DcUserSettingsMapper mapper;

    @Operation(summary = "Get DC User Settings")
    @GetMapping
    public ResponseEntity<DcUserSettingsResponse> getSettings() {
        return dcUserSettingsService.getSettings()
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Update DC User Settings")
    @PutMapping
    public ResponseEntity<DcUserSettingsResponse> updateSettings(@Valid @RequestBody DcUserSettingsRequest request) {
        SaveDcUserSettingsCommand command = mapper.toCommand(request);
        DcUserSettingsResult result = dcUserSettingsService.saveSettings(Objects.requireNonNull(command));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @Operation(summary = "Update Target Allocations for a DC/iDeCo Account Type")
    @PutMapping("/{type}/target-allocations")
    public ResponseEntity<DcUserSettingsResponse> updateTargetAllocations(
            @PathVariable @NonNull DefinedContributionType type,
            @Valid @RequestBody UpdateTargetAllocationsRequest request) {
        var command = mapper.toUpdateAccountAllocationsCommand(request);
        DcUserSettingsResult result = dcUserSettingsService.updateTargetAllocations(type, command);
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @Operation(summary = "Clear Target Allocations for a DC/iDeCo Account Type")
    @DeleteMapping("/{type}/target-allocations")
    public ResponseEntity<DcUserSettingsResponse> clearTargetAllocations(
            @PathVariable @NonNull DefinedContributionType type) {
        return dcUserSettingsService.clearTargetAllocations(type)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    public record UpdateTargetAllocationsRequest(
            @NotBlank String remainderFundCode,
            @NotEmpty @Valid List<TargetFundAllocationRequest> allocations) {
    }

    public record TargetFundAllocationRequest(
            @NotBlank String fundCode,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double allocationPercentage) {
    }
}
