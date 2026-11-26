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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
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

import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.CreateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.UpdateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.FinanceHubConfigResult;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.CreateFinanceHubConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.FinanceHubConfigMapper;
import ch.lin.finance.hub.backend.api.controller.config.dto.system.UpdateFinanceHubConfigRequest;
import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;

@ExtendWith(MockitoExtension.class)
class FinanceHubConfigControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FinanceHubConfigService configService;

    @Spy
    @SuppressWarnings("unused")
    private final FinanceHubConfigMapper configMapper = Mappers.getMapper(FinanceHubConfigMapper.class);

    @InjectMocks
    private FinanceHubConfigController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should get all configs successfully")
    void getAllConfigs_ReturnsAllConfigsResponse() throws Exception {
        AllConfigsData data = new AllConfigsData("default", List.of("default", "custom"));
        when(configService.getAllConfigs()).thenReturn(data);

        mockMvc.perform(get("/configs/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledConfigName").value("default"))
                .andExpect(jsonPath("$.allConfigNames[0]").value("default"))
                .andExpect(jsonPath("$.allConfigNames[1]").value("custom"));

        verify(configService, times(1)).getAllConfigs();
    }

    @Test
    @DisplayName("Should create config successfully")
    void createConfig_ValidRequest_ReturnsCreated() throws Exception {
        CreateFinanceHubConfigRequest request = new CreateFinanceHubConfigRequest();
        request.setName("new-config");
        request.setEnabled(true);
        request.setFileProcessingTimeoutSeconds(60);
        request.setNisaExpectedReturnRate(0.05);
        request.setIdecoExpectedReturnRate(0.04);
        request.setPensionExpectedMonthlySalary(300000L);
        request.setIdecoUnlockAge(60);
        request.setDcUnlockAge(60);
        request.setInflationRate(0.0);

        FinanceHubConfigResult createdConfig = new FinanceHubConfigResult();
        createdConfig.setName("new-config");
        createdConfig.setEnabled(true);
        createdConfig.setFileProcessingTimeoutSeconds(60);
        createdConfig.setSimulationEarlyRepaymentMonth(12);
        createdConfig.setRetirementMonthMode(RetirementMonthMode.DECEMBER);

        when(configService.createConfigResult(any(CreateFinanceHubConfigCommand.class))).thenReturn(createdConfig);

        mockMvc.perform(post("/configs/system")
                .contentType(Objects.requireNonNull(
                        MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        Objects.requireNonNull(org.hamcrest.Matchers
                                .containsString("/configs/system/new-config"))))
                .andExpect(jsonPath("$.data.name").value("new-config"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(12))
                .andExpect(jsonPath("$.data.retirementMonthMode").value("DECEMBER"));

        verify(configService, times(1)).createConfigResult(any(CreateFinanceHubConfigCommand.class));
    }

    @Test
    @DisplayName("Should delete all configs successfully")
    void deleteAllConfigs_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/configs/system"))
                .andExpect(status().isNoContent());

        verify(configService, times(1)).deleteAllConfigs();
    }

    @Test
    @DisplayName("Should get specific config successfully")
    void getConfig_ReturnsConfig() throws Exception {
        FinanceHubConfigResult config = new FinanceHubConfigResult();
        config.setName("test-config");
        config.setEnabled(true);
        config.setFileProcessingTimeoutSeconds(30);

        when(configService.getConfigResult("test-config")).thenReturn(config);

        mockMvc.perform(get("/configs/system/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("test-config"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(configService, times(1)).getConfigResult("test-config");
    }

    @Test
    @DisplayName("Should update config successfully")
    void saveConfig_ValidRequest_ReturnsUpdatedConfig() throws Exception {
        UpdateFinanceHubConfigRequest request = new UpdateFinanceHubConfigRequest();
        request.setEnabled(false);

        FinanceHubConfigResult savedConfig = new FinanceHubConfigResult();
        savedConfig.setName("test-config");
        savedConfig.setEnabled(false);
        savedConfig.setFileProcessingTimeoutSeconds(30);
        savedConfig.setSimulationEarlyRepaymentMonth(6);
        savedConfig.setRetirementMonthMode(RetirementMonthMode.DECEMBER);

        when(configService.saveConfigResult(any(UpdateFinanceHubConfigCommand.class))).thenReturn(savedConfig);

        mockMvc.perform(patch("/configs/system/test-config")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("test-config"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.simulationEarlyRepaymentMonth").value(6))
                .andExpect(jsonPath("$.data.retirementMonthMode").value("DECEMBER"));

        verify(configService, times(1)).saveConfigResult(any(UpdateFinanceHubConfigCommand.class));
    }

    @Test
    @DisplayName("Should delete specific config successfully")
    void deleteConfig_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/configs/system/test-config"))
                .andExpect(status().isNoContent());

        verify(configService, times(1)).deleteConfig("test-config");
    }
}
