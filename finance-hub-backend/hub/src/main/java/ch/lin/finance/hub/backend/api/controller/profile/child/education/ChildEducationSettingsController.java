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
package ch.lin.finance.hub.backend.api.controller.profile.child.education;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.profile.child.education.ChildEducationSettingsService;
import ch.lin.finance.hub.backend.api.app.service.profile.child.education.command.UpdateChildEducationSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.education.result.ChildEducationSettingsResult;
import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.ChildEducationSettingsMapper;
import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.ChildEducationSettingsResponse;
import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.UpdateChildEducationSettingsRequest;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

/**
 * REST controller for managing a specific child's education settings overrides.
 */
@RestController
@RequestMapping("/profile/children/{childProfileId}/education-settings")
public class ChildEducationSettingsController {

    private static final Logger logger = LoggerFactory.getLogger(ChildEducationSettingsController.class);

    private final ChildEducationSettingsService settingsService;
    private final ChildEducationSettingsMapper settingsMapper;

    public ChildEducationSettingsController(ChildEducationSettingsService settingsService,
            ChildEducationSettingsMapper settingsMapper) {
        this.settingsService = settingsService;
        this.settingsMapper = settingsMapper;
    }

    /**
     * Retrieves the education settings for the given child profile ID.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ChildEducationSettingsResponse>> getSettings(
            @PathVariable Long childProfileId) {
        logger.debug("Fetching education settings for childProfileId: {}", childProfileId);
        ChildEducationSettingsResult result = settingsService.getSettings(childProfileId);
        ChildEducationSettingsResponse response = settingsMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates (or creates) the education settings for the given child profile
     * ID.
     */
    @PutMapping
    public ResponseEntity<ApiResponse<ChildEducationSettingsResponse>> updateSettings(
            @PathVariable Long childProfileId,
            @Valid @RequestBody UpdateChildEducationSettingsRequest request) {
        logger.info("Received request to update education settings for childProfileId: {}", childProfileId);
        UpdateChildEducationSettingsCommand command = settingsMapper.toCommand(request);
        ChildEducationSettingsResult updated = settingsService.updateSettings(childProfileId, command);
        ChildEducationSettingsResponse response = settingsMapper.toResponse(updated);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Deletes the education settings for the given child profile ID.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSettings(@PathVariable Long childProfileId) {
        logger.info("Received request to delete education settings for childProfileId: {}", childProfileId);
        settingsService.deleteSettings(childProfileId);
    }
}
