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
import java.util.Optional;

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

import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.CreateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.UpdateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.AllConfigsResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.CreateFinanceHubConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.FinanceHubConfigMapper;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.FinanceHubConfigResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.UpdateFinanceHubConfigRequest;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

/**
 * REST controller for managing global Finance Hub configurations.
 * <p>
 * This controller provides a full set of CRUD (Create, Read, Update, Delete)
 * endpoints for {@link FinanceHubConfig} entities.
 */
@RestController
@RequestMapping("/configs/system")
public class FinanceHubConfigController {

    /**
     * Logger for this controller.
     */
    private static final Logger logger = LoggerFactory.getLogger(FinanceHubConfigController.class);

    /**
     * Service responsible for managing global configurations.
     */
    private final FinanceHubConfigService configService;
    private final FinanceHubConfigMapper configMapper;

    /**
     * Constructs a new FinanceHubConfigController.
     *
     * @param configService The service responsible for managing global Finance
     *                      Hub configurations.
     * @param configMapper  The mapper to convert results to responses.
     */
    public FinanceHubConfigController(FinanceHubConfigService configService, FinanceHubConfigMapper configMapper) {
        this.configService = configService;
        this.configMapper = configMapper;
    }

    /**
     * Retrieves the names of all available global configurations and identifies
     * which one is currently enabled.
     *
     * @return A {@link ResponseEntity} containing an
     *         {@link AllConfigsResponse}.
     */
    @GetMapping
    public ResponseEntity<AllConfigsResponse> getAllConfigs() {
        logger.debug("Fetching all global configurations.");
        AllConfigsData configsData = configService.getAllConfigs();
        AllConfigsResponse response = new AllConfigsResponse(configsData.getEnabledConfigName(),
                configsData.getAllConfigNames());
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new global configuration.
     *
     * @param request The request body containing the details of the new
     *                configuration.
     * @return A {@link ResponseEntity} with an HTTP 201 Created status.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FinanceHubConfigResponse>> createConfig(
            @Valid @RequestBody CreateFinanceHubConfigRequest request) {
        logger.info("Received request to create a new global configuration: {}", request.getName());

        CreateFinanceHubConfigCommand command = CreateFinanceHubConfigCommand.builder()
                .name(request.getName())
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .defaultCurrency(request.getDefaultCurrency())
                .fileProcessingTimeoutSeconds(request.getFileProcessingTimeoutSeconds())
                .nisaExpectedReturnRate(request.getNisaExpectedReturnRate())
                .idecoExpectedReturnRate(request.getIdecoExpectedReturnRate())
                .pensionExpectedMonthlySalary(request.getPensionExpectedMonthlySalary())
                .lifeExpectancy(request.getLifeExpectancy())
                .targetRetirementAge(request.getTargetRetirementAge())
                .pensionStartAge(request.getPensionStartAge())
                .idecoUnlockAge(request.getIdecoUnlockAge())
                .dcUnlockAge(request.getDcUnlockAge())
                .inflationRate(request.getInflationRate())
                .gaPopulationSize(request.getGaPopulationSize())
                .gaMaxGenerations(request.getGaMaxGenerations())
                .gaMutationRate(request.getGaMutationRate())
                .gaEliteRatio(request.getGaEliteRatio())
                .simulationEarlyRepaymentMonth(request.getSimulationEarlyRepaymentMonth())
                .retirementMonthMode(request.getRetirementMonthMode())
                .build();

        FinanceHubConfigResponse createdConfig = configMapper.toResponse(configService.createConfigResult(command));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{name}")
                .buildAndExpand(createdConfig.getName())
                .toUri();
        return ResponseEntity.created(location).body(ApiResponse.success(createdConfig));
    }

    /**
     * Deletes all global configurations.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAllConfigs() {
        logger.info("Received request to delete all global configurations.");
        configService.deleteAllConfigs();
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves a specific global configuration by its name.
     */
    @GetMapping("/{name}")
    public ResponseEntity<FinanceHubConfigResponse> getConfig(@PathVariable("name") String name) {
        logger.debug("Fetching global configuration: {}", name);
        return ResponseEntity.ok(configMapper.toResponse(configService.getConfigResult(name)));
    }

    /**
     * Creates or partially updates a specific global configuration.
     *
     * @param name    The name of the configuration to create or update.
     * @param request The request body containing the configuration fields to
     *                update.
     * @return A {@link ResponseEntity} with an HTTP 200 OK status.
     */
    @PatchMapping("/{name}")
    public ResponseEntity<ApiResponse<FinanceHubConfigResponse>> saveConfig(@PathVariable("name") String name,
            @RequestBody @Valid UpdateFinanceHubConfigRequest request) {
        logger.info("Received request to update/save global configuration: {}", name);

        UpdateFinanceHubConfigCommand command = UpdateFinanceHubConfigCommand.builder()
                .name(name)
                .enabled(Optional.ofNullable(request.getEnabled()))
                .defaultCurrency(Optional.ofNullable(request.getDefaultCurrency()))
                .fileProcessingTimeoutSeconds(Optional.ofNullable(request.getFileProcessingTimeoutSeconds()))
                .nisaExpectedReturnRate(Optional.ofNullable(request.getNisaExpectedReturnRate()))
                .idecoExpectedReturnRate(Optional.ofNullable(request.getIdecoExpectedReturnRate()))
                .pensionExpectedMonthlySalary(Optional.ofNullable(request.getPensionExpectedMonthlySalary()))
                .lifeExpectancy(Optional.ofNullable(request.getLifeExpectancy()))
                .targetRetirementAge(Optional.ofNullable(request.getTargetRetirementAge()))
                .pensionStartAge(Optional.ofNullable(request.getPensionStartAge()))
                .idecoUnlockAge(Optional.ofNullable(request.getIdecoUnlockAge()))
                .dcUnlockAge(Optional.ofNullable(request.getDcUnlockAge()))
                .inflationRate(Optional.ofNullable(request.getInflationRate()))
                .gaPopulationSize(Optional.ofNullable(request.getGaPopulationSize()))
                .gaMaxGenerations(Optional.ofNullable(request.getGaMaxGenerations()))
                .gaMutationRate(Optional.ofNullable(request.getGaMutationRate()))
                .gaEliteRatio(Optional.ofNullable(request.getGaEliteRatio()))
                .simulationEarlyRepaymentMonth(Optional.ofNullable(request.getSimulationEarlyRepaymentMonth()))
                .retirementMonthMode(Optional.ofNullable(request.getRetirementMonthMode()))
                .build();

        FinanceHubConfigResponse savedConfig = configMapper.toResponse(configService.saveConfigResult(command));
        return ResponseEntity.ok(ApiResponse.success(savedConfig));
    }

    /**
     * Deletes a specific global configuration by its name.
     *
     * @param name The name of the configuration to delete.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteConfig(@PathVariable("name") String name) {
        logger.info("Received request to delete global configuration: {}", name);
        configService.deleteConfig(name);
        return ResponseEntity.noContent().build();
    }
}
