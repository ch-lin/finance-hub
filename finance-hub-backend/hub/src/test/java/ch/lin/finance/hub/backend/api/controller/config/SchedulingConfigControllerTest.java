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

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.service.config.scheduler.SchedulingConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.CreateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.UpdateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.result.SchedulingConfigResult;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.result.TimeZoneOption;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.app.service.job.JobOrchestrator;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.CreateSchedulingConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.SchedulingConfigMapper;
import ch.lin.finance.hub.backend.api.controller.config.dto.scheduler.UpdateSchedulingConfigRequest;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
class SchedulingConfigControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SchedulingConfigService configsService;

    @Mock
    private JobOrchestrator orchestrator;

    @Spy
    @SuppressWarnings("unused")
    private final SchedulingConfigMapper configMapper = Mappers.getMapper(SchedulingConfigMapper.class);

    @InjectMocks
    private SchedulingConfigController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should get all transaction source types successfully")
    void getTypes_ReturnsTypes() throws Exception {
        mockMvc.perform(get("/configs/transaction-sources/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("AMAZON"));
    }

    @Test
    @DisplayName("Should get supported transaction sources successfully")
    void getSupported_ReturnsSupportedSources() throws Exception {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        mockMvc.perform(get("/configs/transaction-sources/supported"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("AMAZON"));

        verify(orchestrator, times(1)).getSupportedSources();
    }

    @Test
    @DisplayName("Should get all configs for transaction source successfully")
    void getAllConfigs_ReturnsAllConfigsResponse() throws Exception {
        AllConfigsData data = new AllConfigsData("default", List.of("default", "custom"));
        when(configsService.getAllConfigs(TransactionSource.AMAZON)).thenReturn(data);

        mockMvc.perform(get("/configs/transaction-sources/AMAZON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabledConfigName").value("default"))
                .andExpect(jsonPath("$.data.allConfigNames[0]").value("default"))
                .andExpect(jsonPath("$.data.allConfigNames[1]").value("custom"));

        verify(configsService, times(1)).getAllConfigs(TransactionSource.AMAZON);
    }

    @Test
    @DisplayName("Should create config successfully")
    void createConfig_ValidRequest_ReturnsCreated() throws Exception {
        CreateSchedulingConfigRequest request = new CreateSchedulingConfigRequest();
        request.setName("new-config");
        request.setEnabled(true);

        SchedulingConfigResult createdConfig = new SchedulingConfigResult();
        createdConfig.setName("new-config");
        createdConfig.setTransactionSource(TransactionSource.AMAZON);
        createdConfig.setEnabled(true);

        when(configsService.createConfigResult(any(CreateSchedulingConfigCommand.class)))
                .thenReturn(createdConfig);

        mockMvc.perform(post("/configs/transaction-sources/AMAZON")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        Objects.requireNonNull(org.hamcrest.Matchers.containsString(
                                "/configs/transaction-sources/AMAZON/new-config"))))
                .andExpect(jsonPath("$.data.name").value("new-config"))
                .andExpect(jsonPath("$.data.transactionSource").value("AMAZON"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        verify(configsService, times(1)).createConfigResult(any(CreateSchedulingConfigCommand.class));
    }

    @Test
    @DisplayName("Should delete all configs successfully")
    void deleteAllConfigs_ReturnsOk() throws Exception {
        mockMvc.perform(delete("/configs/transaction-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("All configurations deleted successfully."));

        verify(configsService, times(1)).deleteAllConfigs();
    }

    @Test
    @DisplayName("Should get specific config successfully")
    void getConfig_ReturnsConfig() throws Exception {
        SchedulingConfigResult config = new SchedulingConfigResult();
        config.setName("test-config");
        config.setTransactionSource(TransactionSource.AMAZON);
        config.setEnabled(true);

        when(configsService.getConfigResult(TransactionSource.AMAZON, "test-config")).thenReturn(config);

        mockMvc.perform(get("/configs/transaction-sources/AMAZON/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("test-config"))
                .andExpect(jsonPath("$.data.transactionSource").value("AMAZON"));

        verify(configsService, times(1)).getConfigResult(TransactionSource.AMAZON, "test-config");
    }

    @Test
    @DisplayName("Should get resolved config successfully")
    void getResolvedConfig_ReturnsConfig() throws Exception {
        SchedulingConfigResult config = new SchedulingConfigResult();
        config.setName("test-config");
        config.setTransactionSource(TransactionSource.AMAZON);
        config.setEnabled(true);

        when(configsService.getResolvedConfigResult(TransactionSource.AMAZON, "test-config")).thenReturn(config);

        mockMvc.perform(get("/configs/transaction-sources/AMAZON/test-config/resolved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("test-config"))
                .andExpect(jsonPath("$.data.transactionSource").value("AMAZON"));

        verify(configsService, times(1)).getResolvedConfigResult(TransactionSource.AMAZON, "test-config");
    }

    @Test
    @DisplayName("Should update config successfully")
    void saveConfig_ValidRequest_ReturnsUpdatedConfig() throws Exception {
        UpdateSchedulingConfigRequest request = new UpdateSchedulingConfigRequest();
        request.setEnabled(false);

        SchedulingConfigResult savedConfig = new SchedulingConfigResult();
        savedConfig.setName("test-config");
        savedConfig.setTransactionSource(TransactionSource.AMAZON);
        savedConfig.setEnabled(false);

        when(configsService.saveConfigResult(eq(TransactionSource.AMAZON),
                any(UpdateSchedulingConfigCommand.class))).thenReturn(savedConfig);

        mockMvc.perform(patch("/configs/transaction-sources/AMAZON/test-config")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("test-config"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(configsService, times(1)).saveConfigResult(eq(TransactionSource.AMAZON),
                any(UpdateSchedulingConfigCommand.class));
    }

    @Test
    @DisplayName("Should delete specific config successfully")
    void deleteConfig_ReturnsOk() throws Exception {
        mockMvc.perform(delete("/configs/transaction-sources/AMAZON/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Configuration deleted successfully."));

        verify(configsService, times(1)).deleteConfig(TransactionSource.AMAZON, "test-config");
    }

    @Test
    @DisplayName("Should get system timezones successfully")
    void getTimeZones_ReturnsTimeZones() throws Exception {
        TimeZoneOption tzInfo = new TimeZoneOption("UTC", "Coordinated Universal Time");
        when(configsService.getTimeZones()).thenReturn(List.of(tzInfo));

        mockMvc.perform(get("/configs/transaction-sources/timezones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("UTC"))
                .andExpect(jsonPath("$.data[0].displayName").value("Coordinated Universal Time"));

        verify(configsService, times(1)).getTimeZones();
    }
}
