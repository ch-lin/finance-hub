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

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionJobServiceImpl;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DefinedContributionMapper;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;

@ExtendWith(MockitoExtension.class)
class DefinedContributionControllerTest {

    @Mock
    private DefinedContributionService dcService;

    @Mock
    private DefinedContributionJobServiceImpl dcJobService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private MockedStatic<SecurityUtil> mockedSecurityUtil;

    @BeforeEach
    void setUp() {
        DefinedContributionMapper mapper = Mappers.getMapper(DefinedContributionMapper.class);
        DefinedContributionController controller = new DefinedContributionController(dcService, dcJobService, mapper);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(Objects.requireNonNull(validator))
                .build();

        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("user-1");
    }

    @AfterEach
    void tearDown() {
        if (mockedSecurityUtil != null && !mockedSecurityUtil.isClosed()) {
            mockedSecurityUtil.close();
        }
        if (validator != null) {
            validator.destroy();
        }
    }

    @Test
    @DisplayName("PATCH /assets/dc/accounts/{accountId}/allocations returns 200 and updated account snapshot")
    void testUpdateAccountAllocations_Success() throws Exception {
        DefinedContributionFundSnapshotResult fund1 = new DefinedContributionFundSnapshotResult();
        fund1.setId(101L);
        fund1.setOwnerUserId("user-1");
        fund1.setPrivacyLevel(PrivacyLevel.PRIVATE);
        fund1.setFundName("Fund A");
        fund1.setFundCode("F001");
        fund1.setAllocationPercentage(60.0);
        fund1.setMonthlyContributionAmount(12_000L);

        DefinedContributionFundSnapshotResult fund2 = new DefinedContributionFundSnapshotResult();
        fund2.setId(102L);
        fund2.setOwnerUserId("user-1");
        fund2.setPrivacyLevel(PrivacyLevel.PRIVATE);
        fund2.setFundName("Fund B");
        fund2.setFundCode("F002");
        fund2.setAllocationPercentage(40.0);
        fund2.setMonthlyContributionAmount(8_000L);

        DefinedContributionAccountSnapshotResult result = new DefinedContributionAccountSnapshotResult();
        result.setId(1L);
        result.setOwnerUserId("user-1");
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);
        result.setRecordDate(LocalDate.of(2026, 1, 1));
        result.setType(DefinedContributionType.IDECO);
        result.setMonthlyTotalContribution(20_000L);
        result.setRemainderFundCode("F002");
        result.setFunds(List.of(fund1, fund2));

        when(dcService.updateAccountAllocations(eq(1L), any(UpdateAccountAllocationsCommand.class)))
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

        mockMvc.perform(patch("/assets/dc/accounts/1/allocations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainderFundCode").value("F002"))
                .andExpect(jsonPath("$.funds[0].fundCode").value("F001"))
                .andExpect(jsonPath("$.funds[0].allocationPercentage").value(60.0))
                .andExpect(jsonPath("$.funds[0].monthlyContributionAmount").value(12_000));
    }

    @Test
    @DisplayName("PATCH /assets/dc/accounts/{accountId}/allocations masks remainderFundCode for non-owner")
    void testUpdateAccountAllocations_MasksRemainderFundCodeForNonOwner() throws Exception {
        DefinedContributionAccountSnapshotResult result = new DefinedContributionAccountSnapshotResult();
        result.setId(1L);
        result.setOwnerUserId("other-user");
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);
        result.setRecordDate(LocalDate.of(2026, 1, 1));
        result.setType(DefinedContributionType.IDECO);
        result.setRemainderFundCode("F002");
        result.setFunds(List.of());

        when(dcService.updateAccountAllocations(eq(1L), any(UpdateAccountAllocationsCommand.class)))
                .thenReturn(result);

        String body = """
                {
                  "remainderFundCode": "F002",
                  "allocations": [
                    { "fundCode": "F002", "allocationPercentage": 100.0 }
                  ]
                }
                """;

        mockMvc.perform(patch("/assets/dc/accounts/1/allocations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainderFundCode").doesNotExist());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("PATCH /assets/dc/accounts/{accountId}/allocations returns 400 when remainderFundCode is missing")
    void testUpdateAccountAllocations_BlankRemainderReturns400() throws Exception {
        String body = """
                {
                  "remainderFundCode": "",
                  "allocations": [
                    { "fundCode": "F001", "allocationPercentage": 100.0 }
                  ]
                }
                """;

        mockMvc.perform(patch("/assets/dc/accounts/1/allocations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(body))
                .andExpect(status().isBadRequest());

        verify(dcService, never()).updateAccountAllocations(any(), any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("PATCH /assets/dc/accounts/{accountId}/allocations returns 400 when percentage exceeds 100")
    void testUpdateAccountAllocations_InvalidPercentageReturns400() throws Exception {
        String body = """
                {
                  "remainderFundCode": "F001",
                  "allocations": [
                    { "fundCode": "F001", "allocationPercentage": 120.0 }
                  ]
                }
                """;

        mockMvc.perform(patch("/assets/dc/accounts/1/allocations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(body))
                .andExpect(status().isBadRequest());

        verify(dcService, never()).updateAccountAllocations(any(), any());
    }
}
