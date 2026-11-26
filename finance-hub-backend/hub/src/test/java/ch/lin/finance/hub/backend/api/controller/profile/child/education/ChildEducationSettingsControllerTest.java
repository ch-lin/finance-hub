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

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.service.profile.child.education.ChildEducationSettingsService;
import ch.lin.finance.hub.backend.api.app.service.profile.child.education.result.ChildEducationSettingsResult;
import ch.lin.finance.hub.backend.api.common.advice.WebGlobalExceptionHandler;
import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineException;
import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.ChildEducationSettingsMapper;
import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.StageOverrideRequest;
import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.UpdateChildEducationSettingsRequest;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ChildEducationSettingsControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChildEducationSettingsService settingsService;

    @Spy
    @SuppressWarnings("unused")
    private final ChildEducationSettingsMapper settingsMapper = Mappers.getMapper(ChildEducationSettingsMapper.class);

    @InjectMocks
    private ChildEducationSettingsController controller;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new WebGlobalExceptionHandler())
                .build();
    }

    @Test
    void updateSettings_ValidRequest_ReturnsOk() throws Exception {
        StageOverrideRequest override = StageOverrideRequest.builder()
                .manualStartMonth(4)
                .manualDuration(72)
                .manualTermsPerYear(3)
                .build();

        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .primaryOverride(override)
                .build();

        ChildEducationSettingsResult result = new ChildEducationSettingsResult();
        result.setPrimaryTrack(PrimaryTrack.PUBLIC);

        when(settingsService.updateSettings(eq(1L), any())).thenReturn(result);

        mockMvc.perform(put("/profile/children/1/education-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void updateSettings_InvalidStartMonth_ReturnsBadRequest() throws Exception {
        StageOverrideRequest override = StageOverrideRequest.builder()
                .manualStartMonth(13) // Invalid (> 12)
                .build();

        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .primaryOverride(override)
                .build();

        mockMvc.perform(put("/profile/children/1/education-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateSettings_InvalidDuration_ReturnsBadRequest() throws Exception {
        StageOverrideRequest override = StageOverrideRequest.builder()
                .manualDuration(250) // Invalid (> 240)
                .build();

        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .primaryOverride(override)
                .build();

        mockMvc.perform(put("/profile/children/1/education-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateSettings_InvalidDurationNonMultipleOf12_ReturnsBadRequest() throws Exception {
        StageOverrideRequest override = StageOverrideRequest.builder()
                .manualDuration(18) // Invalid (not a multiple of 12)
                .build();

        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .primaryOverride(override)
                .build();

        mockMvc.perform(put("/profile/children/1/education-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateSettings_InvalidTermsPerYear_ReturnsBadRequest() throws Exception {
        StageOverrideRequest override = StageOverrideRequest.builder()
                .manualTermsPerYear(5) // Invalid (not in [1, 2, 3, 4, 6, 12])
                .build();

        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .primaryOverride(override)
                .build();

        mockMvc.perform(put("/profile/children/1/education-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateSettings_WhenServiceThrowsInvalidEducationTimelineException_ReturnsBadRequest() throws Exception {
        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
                .primaryTrack(PrimaryTrack.PUBLIC)
                .build();

        when(settingsService.updateSettings(eq(1L), any()))
                .thenThrow(new InvalidEducationTimelineException("Timeline Overlap: Start date cannot be earlier than previous stage"));

        mockMvc.perform(put("/profile/children/1/education-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_EDUCATION_TIMELINE"))
                .andExpect(jsonPath("$.data.message").value("Timeline Overlap: Start date cannot be earlier than previous stage"));
    }

    @Test
    void updateSettings_WithPreschoolTwoStage_ReturnsOk() throws Exception {
        StageOverrideRequest override2 = StageOverrideRequest.builder()
                .manualStartMonth(4)
                .manualDuration(24)
                .manualTuition(new BigDecimal("1800000"))
                .build();

        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
                .preschoolTrack(PreschoolTrack.HOME)
                .preschoolSplitEnabled(true)
                .preschoolStage1Duration(12)
                .preschoolTrack2(PreschoolTrack.INTL)
                .preschoolOverride2(override2)
                .build();

        ChildEducationSettingsResult result = new ChildEducationSettingsResult();
        result.setPreschoolTrack(PreschoolTrack.HOME);
        result.setPreschoolSplitEnabled(true);
        result.setPreschoolStage1Duration(12);
        result.setPreschoolTrack2(PreschoolTrack.INTL);

        when(settingsService.updateSettings(eq(1L), any())).thenReturn(result);

        mockMvc.perform(put("/profile/children/1/education-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.preschoolSplitEnabled").value(true))
                .andExpect(jsonPath("$.data.preschoolStage1Duration").value(12))
                .andExpect(jsonPath("$.data.preschoolTrack2").value("INTL"));
    }
}
