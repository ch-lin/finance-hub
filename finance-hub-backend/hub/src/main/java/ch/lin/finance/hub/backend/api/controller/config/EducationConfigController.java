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
package ch.lin.finance.hub.backend.api.controller.config;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.CreateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.UpdateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.result.EducationConfigResult;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.CreateEducationConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.EducationConfigMapper;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.EducationConfigResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.UpdateEducationConfigRequest;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

/**
 * REST controller for managing global Education configurations.
 */
@RestController
@RequestMapping("/configs/education")
public class EducationConfigController {

    private static final Logger logger = LoggerFactory.getLogger(EducationConfigController.class);

    private final EducationConfigService configService;
    private final EducationConfigMapper configMapper;

    public EducationConfigController(EducationConfigService configService, EducationConfigMapper configMapper) {
        this.configService = configService;
        this.configMapper = configMapper;
    }

    /**
     * Retrieves the default education configuration. Aligned with Plan-01
     * specification: GET /configs/education.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<EducationConfigResponse>> getDefaultConfig() {
        logger.debug("Fetching default education configuration.");
        EducationConfigResult result = configService.getDefaultConfig();
        EducationConfigResponse response = configMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves all education configurations.
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<EducationConfigResponse>>> getAllConfigs() {
        logger.debug("Fetching all education configurations.");
        List<EducationConfigResult> results = configService.getAllConfigs();
        List<EducationConfigResponse> responseList = configMapper.toResponseList(results);
        return ResponseEntity.ok(ApiResponse.success(responseList));
    }

    /**
     * Retrieves a specific education configuration by name.
     */
    @GetMapping("/{name}")
    public ResponseEntity<ApiResponse<EducationConfigResponse>> getConfig(@PathVariable String name) {
        logger.debug("Fetching education configuration: {}", name);
        EducationConfigResult result = configService.getConfig(name);
        EducationConfigResponse response = configMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Creates a new education configuration.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<EducationConfigResponse>> createConfig(
            @Valid @RequestBody CreateEducationConfigRequest request) {
        logger.info("Received request to create a new education configuration: {}", request.getName());
        CreateEducationConfigCommand command = configMapper.toCommand(request);
        EducationConfigResult created = configService.createConfig(command);
        EducationConfigResponse response = configMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{name}")
                .buildAndExpand(response.getName())
                .toUri();

        return ResponseEntity.created(location).body(ApiResponse.success(response));
    }

    /**
     * Updates an existing education configuration.
     */
    @PatchMapping("/{name}")
    public ResponseEntity<ApiResponse<EducationConfigResponse>> updateConfig(
            @PathVariable String name,
            @Valid @RequestBody UpdateEducationConfigRequest request) {
        logger.info("Received request to update education configuration: {}", name);
        UpdateEducationConfigCommand command = configMapper.toCommand(request);
        EducationConfigResult updated = configService.updateConfig(name, command);
        EducationConfigResponse response = configMapper.toResponse(updated);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Deletes an education configuration by its name.
     */
    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(@PathVariable String name) {
        logger.info("Received request to delete education configuration: {}", name);
        configService.deleteConfig(name);
    }
}
