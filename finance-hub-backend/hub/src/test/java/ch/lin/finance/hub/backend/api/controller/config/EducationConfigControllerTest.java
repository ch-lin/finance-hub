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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.service.config.education.EducationConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.UpdateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.result.EducationConfigResult;
import ch.lin.finance.hub.backend.api.common.advice.WebGlobalExceptionHandler;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.CreateEducationConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.EducationConfigMapper;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.EducationConfigResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.PreschoolConfigResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.PrimaryConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.TrackConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.TrackConfigResponse;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.UniversityConfigRequest;
import ch.lin.finance.hub.backend.api.controller.config.dto.education.UpdateEducationConfigRequest;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class EducationConfigControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private EducationConfigService configService;

    @Mock
    private EducationConfigMapper configMapper;

    @InjectMocks
    private EducationConfigController controller;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new WebGlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should return 400 when startMonth is invalid (> 12)")
    void updateConfig_InvalidStartMonth_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .startMonth(15) // Invalid (> 12)
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when duration is invalid (> 240)")
    void updateConfig_InvalidDuration_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .duration(250) // Invalid (> 240)
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when termsPerYear is not in whitelist")
    void updateConfig_InvalidTermsPerYear_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .termsPerYear(5) // Invalid (not in [1, 2, 3, 4, 6, 12])
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when tuition is negative")
    void updateConfig_NegativeTuition_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .tuition(BigDecimal.valueOf(-100)) // Negative
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when entranceFee is negative")
    void updateConfig_NegativeEntranceFee_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .entranceFee(BigDecimal.valueOf(-50)) // Negative
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when university livingCost is negative")
    void updateConfig_NegativeLivingCost_ReturnsBadRequest() throws Exception {
        UniversityConfigRequest university = UniversityConfigRequest.builder()
                .livingCost(BigDecimal.valueOf(-500)) // Negative
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .university(university)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when university country livingCost is negative")
    void updateConfig_NegativeCountryLivingCost_ReturnsBadRequest() throws Exception {
        UniversityConfigRequest university = UniversityConfigRequest.builder()
                .usLivingCost(BigDecimal.valueOf(-500)) // Negative
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .university(university)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when createConfig has invalid startMonth")
    void createConfig_InvalidStartMonth_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .startMonth(13) // Invalid (> 12)
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        CreateEducationConfigRequest request = CreateEducationConfigRequest.builder()
                .name("custom")
                .primary(primary)
                .build();

        mockMvc.perform(post("/configs/education")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 200 when update request is valid")
    void updateConfig_ValidRequest_ReturnsOk() throws Exception {
        TrackConfigRequest validTrack = TrackConfigRequest.builder()
                .tuition(BigDecimal.valueOf(500000))
                .entranceFee(BigDecimal.valueOf(100000))
                .duration(72)
                .startMonth(4)
                .termsPerYear(2)
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(validTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        when(configMapper.toCommand(any(UpdateEducationConfigRequest.class))).thenReturn(mock(UpdateEducationConfigCommand.class));
        when(configService.updateConfig(eq("default"), any(UpdateEducationConfigCommand.class))).thenReturn(mock(EducationConfigResult.class));
        when(configMapper.toResponse(any(EducationConfigResult.class))).thenReturn(EducationConfigResponse.builder().name("default").build());

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Should return 200 with standardEntranceAge for tracks when getting default config")
    void getDefaultConfig_ReturnsStandardEntranceAge() throws Exception {
        TrackConfigResponse daycareResponse = TrackConfigResponse.builder()
                .tuition(BigDecimal.valueOf(600000))
                .entranceFee(BigDecimal.valueOf(50000))
                .duration(36)
                .startMonth(4)
                .termsPerYear(1)
                .standardEntranceAge(3)
                .build();

        PreschoolConfigResponse preschool = PreschoolConfigResponse.builder()
                .daycareTrack(daycareResponse)
                .build();

        EducationConfigResponse response = EducationConfigResponse.builder()
                .name("default")
                .preschool(preschool)
                .build();

        EducationConfigResult mockResult = mock(EducationConfigResult.class);
        when(configService.getDefaultConfig()).thenReturn(mockResult);
        when(configMapper.toResponse(mockResult)).thenReturn(response);

        mockMvc.perform(get("/configs/education")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.preschool.daycareTrack.standardEntranceAge").value(3));
    }

    @Test
    @DisplayName("Should return 400 when standardEntranceAge is negative")
    void updateConfig_NegativeStandardEntranceAge_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .standardEntranceAge(-1)
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 400 when standardEntranceAge exceeds 30")
    void updateConfig_ExceedMaxStandardEntranceAge_ReturnsBadRequest() throws Exception {
        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
                .standardEntranceAge(31)
                .build();

        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
                .publicTrack(invalidTrack)
                .build();

        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
                .primary(primary)
                .build();

        mockMvc.perform(patch("/configs/education/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }
}

