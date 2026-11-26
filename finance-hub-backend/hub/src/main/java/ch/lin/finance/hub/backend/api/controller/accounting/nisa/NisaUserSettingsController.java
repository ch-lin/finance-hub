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

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaUserSettingsService;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.SaveNisaUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaUserSettingsResult;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaUserSettingsMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaUserSettingsRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaUserSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/assets/nisa/user-settings")
@RequiredArgsConstructor
@Tag(name = "NISA Configuration")
public class NisaUserSettingsController {

    private final NisaUserSettingsService nisaUserSettingsService;
    private final NisaUserSettingsMapper mapper;

    @Operation(summary = "Get NISA User Settings")
    @GetMapping
    public ResponseEntity<NisaUserSettingsResponse> getSettings() {
        return nisaUserSettingsService.getSettings()
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Update NISA User Settings")
    @PutMapping
    public ResponseEntity<NisaUserSettingsResponse> updateSettings(
            @Valid @RequestBody NisaUserSettingsRequest request) {
        SaveNisaUserSettingsCommand command = mapper.toCommand(request);
        NisaUserSettingsResult result = nisaUserSettingsService.saveSettings(Objects.requireNonNull(command));
        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
