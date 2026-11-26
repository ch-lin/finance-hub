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

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.service.config.pension.PensionRuleConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.CreatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
import ch.lin.finance.hub.backend.api.controller.config.dto.pension.CreatePensionRuleConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.pension.PensionRuleConfigMapper;

@ExtendWith(MockitoExtension.class)
class PensionRuleConfigControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PensionRuleConfigService configService;

    @Spy
    @SuppressWarnings("unused")
    private final PensionRuleConfigMapper configMapper = Mappers.getMapper(PensionRuleConfigMapper.class);

    @InjectMocks
    private PensionRuleConfigController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should get all configs successfully")
    void getAllConfigs_ReturnsAllConfigsResponse() throws Exception {
        AllPensionRuleConfigsData data = new AllPensionRuleConfigsData("default", List.of("default", "custom"));
        when(configService.getAllConfigs()).thenReturn(data);

        mockMvc.perform(get("/configs/pension-rule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledConfigName").value("default"))
                .andExpect(jsonPath("$.allConfigNames[0]").value("default"))
                .andExpect(jsonPath("$.allConfigNames[1]").value("custom"));

        verify(configService, times(1)).getAllConfigs();
    }

    @Test
    @DisplayName("Should create config successfully")
    void createConfig_ReturnsCreatedConfig() throws Exception {
        CreatePensionRuleConfigRequest request = new CreatePensionRuleConfigRequest();
        request.setName("custom");
        request.setEnabled(true);
        request.setMaxBasicPensionAmount(831700L);
        request.setMaxBasicPensionMonths(480L);
        request.setKouseiMultiplierPerMille(new BigDecimal("5.481"));
        request.setKouseiSalaryCapMonthly(650000L);
        request.setKouseiBonusCapPerPayment(1500000L);

        PensionRuleConfigResult result = PensionRuleConfigResult.builder()
                .name("custom")
                .enabled(true)
                .maxBasicPensionAmount(831700L)
                .maxBasicPensionMonths(480L)
                .kouseiMultiplierPerMille(new BigDecimal("5.481"))
                .kouseiSalaryCapMonthly(650000L)
                .kouseiBonusCapPerPayment(1500000L)
                .build();

        when(configService.createConfig(any(CreatePensionRuleConfigCommand.class))).thenReturn(result);

        mockMvc.perform(post("/configs/pension-rule")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("custom"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxBasicPensionMonths").value(480))
                .andExpect(jsonPath("$.data.kouseiMultiplierPerMille").value(5.481))
                .andExpect(jsonPath("$.data.kouseiSalaryCapMonthly").value(650000))
                .andExpect(jsonPath("$.data.kouseiBonusCapPerPayment").value(1500000));
    }
}
