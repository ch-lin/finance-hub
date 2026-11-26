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
package ch.lin.finance.hub.backend.api.controller.accounting.nisa;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaProjectionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaSnapshotRecordService;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaProjectionYearlyResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaSnapshotMapper;

@ExtendWith(MockitoExtension.class)
class NisaSnapshotRecordControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NisaSnapshotRecordService nisaSnapshotRecordService;

    @Mock
    @SuppressWarnings("unused")
    private NisaSnapshotMapper nisaSnapshotMapper;

    @Mock
    private NisaProjectionService nisaProjectionService;

    @InjectMocks
    private NisaSnapshotRecordController controller;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .build();
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth = 
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(jwtAuth);
    }

    @Test
    @DisplayName("Should return all snapshot records")
    void getAllRecords_ReturnsList() throws Exception {
        when(nisaSnapshotRecordService.getAllRecords()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/assets/nisa"))
                .andExpect(status().isOk());

        verify(nisaSnapshotRecordService, times(1)).getAllRecords();
    }

    @Test
    @DisplayName("Should return nisa projection")
    void getProjection_ReturnsList() throws Exception {
        NisaProjectionYearlyResult dto = new NisaProjectionYearlyResult(2025, 150000L, 200000L, 50000L);
        NisaProjectionYearlyResponse response = new NisaProjectionYearlyResponse(2025, 150000L, 200000L, 50000L);
        when(nisaProjectionService.calculateProjection(eq(30))).thenReturn(List.of(dto));
        when(nisaSnapshotMapper.toMaskedResponse(eq(dto), org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(get("/assets/nisa/projection")
                .param("years", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").value(2025))
                .andExpect(jsonPath("$[0].projectedPrincipal").value(150000))
                .andExpect(jsonPath("$[0].projectedMarketValue").value(200000))
                .andExpect(jsonPath("$[0].projectedTotalGain").value(50000));

        verify(nisaProjectionService, times(1)).calculateProjection(30);
    }
}
