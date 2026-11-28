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

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.controller.security.AbstractMultiUserIsolationIntegrationTest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;

class NisaProjectionIsolationIntegrationTest extends AbstractMultiUserIsolationIntegrationTest {

    @Autowired
    private NisaSnapshotRecordRepository nisaSnapshotRecordRepository;

    @Override
    protected void cleanUpModuleData() {
        nisaSnapshotRecordRepository.deleteAll();
    }

    @Test
    void nisaProjection_shouldStayScopedToCurrentUserEvenInFamilyView() throws Exception {
        setAuthenticatedUser(USER_A);
        NisaSnapshotRecord nisaA = new NisaSnapshotRecord(
                LocalDate.of(2026, 1, 1), "NISA", "F-A", "Fund A",
                10.0, 100.0, 100.0, 100_000L, 100_000L, 0L);
        nisaA.updateExpectedReturnRate(0.0);
        nisaSnapshotRecordRepository.save(nisaA);

        setAuthenticatedUser(USER_B);
        // Intentionally newer date + SHARED to verify User B never overrides User A
        NisaSnapshotRecord nisaB = new NisaSnapshotRecord(
                LocalDate.of(2026, 2, 1), "NISA", "F-B", "Fund B",
                10.0, 100.0, 100.0, 9_000_000L, 9_000_000L, 0L);
        nisaB.setPrivacyLevel(PrivacyLevel.SHARED);
        nisaB.updateExpectedReturnRate(0.0);
        nisaSnapshotRecordRepository.save(nisaB);

        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/assets/nisa/projection")
                .param("years", "1")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].projectedPrincipal").value(100_000))
                .andExpect(jsonPath("$[0].projectedMarketValue").value(100_000));

        // User C has no NISA records and must receive an empty array
        mockMvc.perform(get("/assets/nisa/projection")
                .param("years", "1")
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
