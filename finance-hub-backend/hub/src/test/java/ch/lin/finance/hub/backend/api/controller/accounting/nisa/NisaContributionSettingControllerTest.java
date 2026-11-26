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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaContributionSettingService;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaContributionSettingResult;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaContributionSettingMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaContributionSettingResponse;

@ExtendWith(MockitoExtension.class)
class NisaContributionSettingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NisaContributionSettingService settingService;

    @Mock
    private NisaContributionSettingMapper settingMapper;

    @InjectMocks
    private NisaContributionSettingController controller;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should return all settings including accountType field to prevent regression (issue-09-01)")
    void getAllSettings_ReturnsListWithAccountType() throws Exception {
        // Arrange: Service returns a result with accountType
        NisaContributionSettingResult result = new NisaContributionSettingResult();
        result.setId(1L);
        result.setFundName("eMAXIS Slim 全世界株式");
        result.setAccountType("NISA（つみたて投資枠）");

        // Arrange: Mapper correctly maps it to response
        NisaContributionSettingResponse response = NisaContributionSettingResponse.builder()
                .id("1")
                .fundName("eMAXIS Slim 全世界株式")
                .accountType("NISA（つみたて投資枠）")
                .build();

        when(settingService.getAllSettings()).thenReturn(List.of(result));
        when(settingMapper.toResponse(result)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/assets/nisa/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fundName").value("eMAXIS Slim 全世界株式"))
                .andExpect(jsonPath("$[0].accountType").value("NISA（つみたて投資枠）"));
    }
}
