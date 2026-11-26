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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.CreatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.UpdatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.controller.config.dto.pension.AllPensionRuleConfigsResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.pension.CreatePensionRuleConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.pension.PensionRuleConfigMapper;
import ch.lin.finance.hub.backend.api.controller.config.dto.pension.PensionRuleConfigResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.pension.UpdatePensionRuleConfigRequest;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

/**
 * REST controller for managing global Pension Rule configurations.
 */
@RestController
@RequestMapping("/configs/pension-rule")
public class PensionRuleConfigController {

    private static final Logger logger = LoggerFactory.getLogger(PensionRuleConfigController.class);

    private final PensionRuleConfigService configService;
    private final PensionRuleConfigMapper configMapper;

    public PensionRuleConfigController(PensionRuleConfigService configService, PensionRuleConfigMapper configMapper) {
        this.configService = configService;
        this.configMapper = configMapper;
    }

    /**
     * Retrieves the names of all available pension rule configurations and
     * identifies
     * which one is currently enabled.
     */
    @GetMapping
    public ResponseEntity<AllPensionRuleConfigsResponse> getAllConfigs() {
        logger.debug("Fetching all pension rule configurations.");
        AllPensionRuleConfigsData configsData = configService.getAllConfigs();
        AllPensionRuleConfigsResponse response = new AllPensionRuleConfigsResponse(configsData.getEnabledConfigName(),
                configsData.getAllConfigNames());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a specific pension rule configuration by name.
     */
    @GetMapping("/{name}")
    public ResponseEntity<ApiResponse<PensionRuleConfigResponse>> getConfig(@PathVariable String name) {
        logger.debug("Fetching pension rule configuration: {}", name);
        PensionRuleConfigResponse response = configMapper.toResponse(configService.getConfig(name));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Creates a new pension rule configuration.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PensionRuleConfigResponse>> createConfig(
            @Valid @RequestBody CreatePensionRuleConfigRequest request) {
        logger.info("Received request to create a new pension rule configuration: {}", request.getName());

        CreatePensionRuleConfigCommand command = configMapper.toCommand(request);

        PensionRuleConfigResponse createdConfig = configMapper.toResponse(configService.createConfig(command));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{name}")
                .buildAndExpand(createdConfig.getName())
                .toUri();

        return ResponseEntity.created(location)
                .body(ApiResponse.success(createdConfig));
    }

    /**
     * Partially updates an existing pension rule configuration.
     */
    @PatchMapping("/{name}")
    public ResponseEntity<ApiResponse<PensionRuleConfigResponse>> updateConfig(
            @PathVariable String name,
            @Valid @RequestBody UpdatePensionRuleConfigRequest request) {
        logger.info("Received request to update pension rule configuration: {}", name);

        UpdatePensionRuleConfigCommand command = configMapper.toCommand(request);

        PensionRuleConfigResponse updatedConfig = configMapper.toResponse(configService.updateConfig(name, command));
        return ResponseEntity.ok(ApiResponse.success(updatedConfig));
    }

    /**
     * Deletes a pension rule configuration.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String name) {
        logger.info("Received request to delete pension rule configuration: {}", name);
        configService.deleteConfig(name);
        return ResponseEntity.noContent().build();
    }
}
