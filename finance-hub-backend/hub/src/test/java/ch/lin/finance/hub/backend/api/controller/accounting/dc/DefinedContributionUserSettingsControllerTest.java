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
package ch.lin.finance.hub.backend.api.controller.accounting.dc;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionUserSettingsService;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DcUserSettingsMapper;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DcTargetAllocationSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;

@ExtendWith(MockitoExtension.class)
class DefinedContributionUserSettingsControllerTest {

    @Mock
    private DefinedContributionUserSettingsService dcUserSettingsService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        DcUserSettingsMapper mapper = Mappers.getMapper(DcUserSettingsMapper.class);
        DefinedContributionUserSettingsController controller = new DefinedContributionUserSettingsController(
                dcUserSettingsService, mapper);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(Objects.requireNonNull(validator))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (validator != null) {
            validator.destroy();
        }
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("PUT /assets/dc/user-settings/{type}/target-allocations returns 200 and target allocation JSON")
    void testUpdateTargetAllocations_Success() throws Exception {
        DcUserSettingsResult result = new DcUserSettingsResult();
        result.setIdecoTargetAllocation(new DcTargetAllocationSetting(
                "F002", Map.of("F001", 60.0, "F002", 40.0)));

        when(dcUserSettingsService.updateTargetAllocations(
                eq(DefinedContributionType.IDECO), any(UpdateAccountAllocationsCommand.class)))
                .thenReturn(result);

        String body = """
                {
                  "remainderFundCode": "F002",
                  "allocations": [
                    { "fundCode": "F001", "allocationPercentage": 60.0 },
                    { "fundCode": "F002", "allocationPercentage": 40.0 }
                  ]
                }
                """;

        mockMvc.perform(put("/assets/dc/user-settings/IDECO/target-allocations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idecoTargetAllocation.remainderFundCode").value("F002"))
                .andExpect(jsonPath("$.idecoTargetAllocation.allocations.F001").value(60.0))
                .andExpect(jsonPath("$.idecoTargetAllocation.allocations.F002").value(40.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("PUT /assets/dc/user-settings/{type}/target-allocations returns 400 when remainderFundCode is blank")
    void testUpdateTargetAllocations_BlankRemainderReturns400() throws Exception {
        String body = """
                {
                  "remainderFundCode": "",
                  "allocations": [
                    { "fundCode": "F001", "allocationPercentage": 100.0 }
                  ]
                }
                """;

        mockMvc.perform(put("/assets/dc/user-settings/CORPORATE_DC/target-allocations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(body))
                .andExpect(status().isBadRequest());

        verify(dcUserSettingsService, never()).updateTargetAllocations(any(), any());
    }

    @Test
    @DisplayName("DELETE /assets/dc/user-settings/{type}/target-allocations returns 200 when settings exist")
    void testClearTargetAllocations_Success() throws Exception {
        DcUserSettingsResult result = new DcUserSettingsResult();
        result.setDcTargetAllocation(new DcTargetAllocationSetting("F001", Map.of("F001", 100.0)));
        result.setIdecoTargetAllocation(null);

        when(dcUserSettingsService.clearTargetAllocations(DefinedContributionType.IDECO))
                .thenReturn(Optional.of(result));

        mockMvc.perform(delete("/assets/dc/user-settings/IDECO/target-allocations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dcTargetAllocation.remainderFundCode").value("F001"))
                .andExpect(jsonPath("$.idecoTargetAllocation").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /assets/dc/user-settings/{type}/target-allocations returns 204 when no settings exist")
    void testClearTargetAllocations_NoContent() throws Exception {
        when(dcUserSettingsService.clearTargetAllocations(DefinedContributionType.IDECO))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/assets/dc/user-settings/IDECO/target-allocations"))
                .andExpect(status().isNoContent());
    }
}
