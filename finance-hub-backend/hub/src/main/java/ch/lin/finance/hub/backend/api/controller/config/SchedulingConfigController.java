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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

import ch.lin.finance.hub.backend.api.app.service.config.scheduler.SchedulingConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.CreateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.UpdateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.app.service.job.JobOrchestrator;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.CreateSchedulingConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.SchedulingConfigMapper;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.SchedulingConfigResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.TimeZoneResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.UpdateSchedulingConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.AllConfigsResponse;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.platform.api.ApiResponse;
import jakarta.validation.Valid;

/**
 * REST controller for managing transaction source configurations.
 * <p>
 * This controller provides a full set of CRUD (Create, Read, Update, Delete)
 * endpoints for {@link SchedulingConfig} entities. It allows clients to
 * list, create, view, update, and delete different configurations for the
 * transaction sources. It delegates the business logic to the
 * {@link SchedulingConfigService}.
 */
@RestController
@RequestMapping("/configs/transaction-sources")
public class SchedulingConfigController {

    /** Logger for this controller. */
    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfigController.class);

    /** Service managing transaction source configurations. */
    private final SchedulingConfigService configsService;
    private final SchedulingConfigMapper configMapper;

    /** Orchestrator used for managing job execution. */
    private final JobOrchestrator orchestrator;

    /**
     * Constructs a new SchedulingConfigController.
     *
     * @param configsService The service managing transaction source configurations.
     * @param orchestrator   The orchestrator used for managing job execution.
     * @param configMapper   The mapper to convert results to responses.
     */
    public SchedulingConfigController(SchedulingConfigService configsService,
            JobOrchestrator orchestrator,
            SchedulingConfigMapper configMapper) {
        this.configsService = configsService;
        this.orchestrator = orchestrator;
        this.configMapper = configMapper;
    }

    /**
     * Retrieves all available transaction sources.
     *
     * @return A {@link ResponseEntity} containing a list of available
     *         transaction source names (e.g., ["AMAZON", "RAKUTEN"]).
     *         <p>
     *         Example cURL request:
     * 
     *         <pre>
     * {@code curl -X GET http://localhost:8081/configs/transaction-sources/types}
     * </pre>
     */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<String>>> getTypes() {
        logger.debug("Fetching all transaction source types.");
        List<String> sources = Arrays.stream(TransactionSource.values())
                .map(source -> source.name())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(sources));
    }

    /**
     * Retrieves a list of all transaction sources that currently have an active
     * executor strategy registered in the system.
     *
     * @return A {@link ResponseEntity} containing a set of active
     *         {@link TransactionSource}.
     *         <p>
     *         Example cURL request:
     * 
     *         <pre>{@code curl -X GET http://localhost:8081/configs/transaction-sources/supported}</pre>
     */
    @GetMapping("/supported")
    public ResponseEntity<ApiResponse<Set<TransactionSource>>> getSupported() {
        logger.debug("Fetching supported transaction sources.");
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getSupportedSources()));
    }

    /**
     * Retrieves the names of all available hub configurations and identifies
     * which one is currently enabled.
     *
     * @param transactionSource The transaction source to filter by.
     * @return A {@link ResponseEntity} containing an {@link AllConfigsResponse}
     *         which includes the name of the enabled configuration and a list of
     *         all
     *         available configuration names.
     *         <p>
     *         Example cURL request:
     *
     *         <pre>
     * {@code
     * curl -X GET http://localhost:8081/configs/transaction-sources/AMAZON
     * }
     * </pre>
     */
    @GetMapping("/{transactionSource}")
    public ResponseEntity<ApiResponse<AllConfigsResponse>> getAllConfigs(
            @PathVariable("transactionSource") TransactionSource transactionSource) {
        logger.debug("Fetching all configurations for transaction source: {}", transactionSource);
        AllConfigsData configsData = configsService.getAllConfigs(transactionSource);
        AllConfigsResponse response = new AllConfigsResponse(configsData.getEnabledConfigName(),
                configsData.getAllConfigNames());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Creates a new hub configuration.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param request           The request body containing the details of the new
     *                          configuration.
     * @return A {@link ResponseEntity} with an HTTP 201 Created status. The
     *         'Location' header contains the URL to the new resource, and the body
     *         contains an {@link ApiResponse} wrapping the created
     *         {@link SchedulingConfig}.
     *         <p>
     *         Example cURL request:
     *
     *         <pre>
     * {@code
     * curl -X POST http://localhost:8081/configs/transaction-sources/AMAZON \
     * -H "Content-Type: application/json" \
     * -d '{
     *   "name": "custom-config",
     *   "enabled": false,
     *   "autoStartImportScheduler": false
     * }'
     * }
     * </pre>
     */
    @PostMapping("/{transactionSource}")
    public ResponseEntity<ApiResponse<SchedulingConfigResponse>> createConfig(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @Valid @RequestBody CreateSchedulingConfigRequest request) {
        logger.info("Received request to create new config '{}' for transaction source: {}", request.getName(),
                transactionSource);

        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                request.getName(),
                transactionSource,
                Boolean.TRUE.equals(request.getEnabled()),
                request.getAutoStartImportScheduler(),
                request.getSchedulerType(),
                request.getFixedRate(),
                request.getCronExpression(),
                request.getCronTimeZone());

        SchedulingConfigResponse createdConfig = configMapper.toResponse(configsService.createConfigResult(command));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{name}")
                .buildAndExpand(createdConfig.getName())
                .toUri();
        return ResponseEntity.created(location).body(ApiResponse.success(createdConfig));
    }

    /**
     * Deletes all hub configurations.
     * <p>
     * This endpoint provides a way to reset all configurations. After deletion,
     * the system will automatically recreate the 'default' configuration on the
     * next relevant request.
     *
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status upon
     *         successful deletion.
     *         <p>
     *         Example cURL request:
     *
     *         <pre>
     * {@code curl -X DELETE http://localhost:8081/configs}
     * </pre>
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> deleteAllConfigs() {
        logger.info("Received request to delete all transaction source configurations.");
        configsService.deleteAllConfigs();
        return ResponseEntity.ok(ApiResponse.success("All configurations deleted successfully."));
    }

    /**
     * Retrieves a specific hub configuration by its name.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param name              The name of the configuration to retrieve, passed as
     *                          a path
     *                          variable.
     * @return A {@link ResponseEntity} containing the
     *         {@link SchedulingConfig} if found. Throws an exception
     *         resulting
     *         in a 404 Not Found status if no configuration with the given name
     *         exists.
     *         <p>
     *         Example cURL request:
     *
     *         <pre>
     * {@code
     * curl -X GET http://localhost:8081/configs/transaction-sources/AMAZON/default
     * }
     * </pre>
     */
    @GetMapping("/{transactionSource}/{name}")
    public ResponseEntity<ApiResponse<SchedulingConfigResponse>> getConfig(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @PathVariable("name") String name) {
        logger.debug("Fetching configuration '{}' for transaction source: {}", name, transactionSource);
        return ResponseEntity.ok(
                ApiResponse.success(configMapper.toResponse(configsService.getConfigResult(transactionSource, name))));
    }

    /**
     * Resolves and returns the appropriate hub configuration, filling any
     * missing fields with defaults from the application properties.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param name              The name of the configuration to resolve, passed as
     *                          a path
     *                          variable.
     * @return A {@link ResponseEntity} containing the resolved
     *         {@link SchedulingConfig}.
     *         <p>
     *         Example cURL request:
     * 
     *         <pre>
     * {@code curl -X GET http://localhost:8081/configs/transaction-sources/AMAZON/default/resolved}
     * </pre>
     */
    @GetMapping("/{transactionSource}/{name}/resolved")
    public ResponseEntity<ApiResponse<SchedulingConfigResponse>> getResolvedConfig(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @PathVariable("name") String name) {
        logger.debug("Fetching resolved configuration '{}' for transaction source: {}", name, transactionSource);
        return ResponseEntity.ok(ApiResponse
                .success(configMapper.toResponse(configsService.getResolvedConfigResult(transactionSource, name))));
    }

    /**
     * Creates or partially updates a specific hub configuration.
     * <p>
     * If a configuration with the given name exists, it will be updated with
     * the non-null fields from the request body. If it does not exist, a new
     * configuration will be created with the given name and fields.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param name              The name of the configuration to create or update.
     * @param request           The request body containing the configuration fields
     *                          to
     *                          update.
     * @return A {@link ResponseEntity} with an HTTP 200 OK status, containing
     *         an {@link ApiResponse} that wraps the saved
     *         {@link SchedulingConfig}.
     *         <p>
     *         Example cURL request to create/update a config:
     *
     *         <pre>
     * {@code
     * curl -X PATCH http://localhost:8081/configs/transaction-sources/AMAZON/my-config \
     * -H "Content-Type: application/json" \
     * -d '{
     *   "enabled": true,
     *   "autoStartImportScheduler": true
     * }'
     * }
     * </pre>
     */
    @PatchMapping("/{transactionSource}/{name}")
    public ResponseEntity<ApiResponse<SchedulingConfigResponse>> saveConfig(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @PathVariable("name") String name,
            @RequestBody @Valid UpdateSchedulingConfigRequest request) {
        logger.info("Received request to save/update config '{}' for transaction source: {}", name, transactionSource);

        UpdateSchedulingConfigCommand command = UpdateSchedulingConfigCommand.builder()
                .name(name)
                .enabled(Optional.ofNullable(request.getEnabled()))
                .autoStartImportScheduler(Optional.ofNullable(request.getAutoStartImportScheduler()))
                .schedulerType(Optional.ofNullable(request.getSchedulerType()))
                .fixedRate(Optional.ofNullable(request.getFixedRate()))
                .cronExpression(Optional.ofNullable(request.getCronExpression()))
                .cronTimeZone(Optional.ofNullable(request.getCronTimeZone()))
                .build();

        SchedulingConfigResponse savedConfig = configMapper
                .toResponse(configsService.saveConfigResult(transactionSource, command));
        return ResponseEntity.ok(ApiResponse.success(savedConfig));
    }

    /**
     * Deletes a specific hub configuration by its name.
     * <p>
     * The 'default' configuration is system-reserved and cannot be deleted.
     *
     * @param transactionSource The transaction source (e.g., AMAZON).
     * @param name              The name of the configuration to delete.
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status.
     *         Throws an exception resulting in a 404 Not Found if the configuration
     *         does not exist.
     *         <p>
     *         Example cURL request:
     *
     *         <pre>
     * {@code curl -X DELETE http://localhost:8081/configs/transaction-sources/AMAZON/custom-config}
     * </pre>
     */
    @DeleteMapping("/{transactionSource}/{name}")
    public ResponseEntity<ApiResponse<String>> deleteConfig(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @PathVariable("name") String name) {
        logger.info("Received request to delete config '{}' for transaction source: {}", name, transactionSource);
        configsService.deleteConfig(transactionSource, name);
        return ResponseEntity.ok(ApiResponse.success("Configuration deleted successfully."));
    }

    /**
     * Retrieves a list of clean and sorted time zones.
     *
     * @return A {@link ResponseEntity} containing a list of
     *         {@link TimeZoneResponse}.
     */
    @GetMapping("/timezones")
    public ResponseEntity<ApiResponse<List<TimeZoneResponse>>> getTimeZones() {
        logger.debug("Fetching system timezones.");
        List<TimeZoneResponse> timeZones = configsService.getTimeZones().stream()
                .map(tz -> new TimeZoneResponse(tz.id(), tz.displayName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(timeZones));
    }
}
