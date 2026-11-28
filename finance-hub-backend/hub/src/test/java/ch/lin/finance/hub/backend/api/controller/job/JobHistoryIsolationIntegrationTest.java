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
package ch.lin.finance.hub.backend.api.controller.job;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.lin.finance.hub.backend.api.app.repository.job.ImportJobHistoryRepository;
import ch.lin.finance.hub.backend.api.controller.security.AbstractMultiUserIsolationIntegrationTest;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;

class JobHistoryIsolationIntegrationTest extends AbstractMultiUserIsolationIntegrationTest {

    @Autowired
    private ImportJobHistoryRepository importJobHistoryRepository;

    @Override
    protected void cleanUpModuleData() {
        importJobHistoryRepository.deleteAll();
    }

    @Test
    void jobHistory_shouldStayScopedToCurrentUserEvenInFamilyView() throws Exception {
        setAuthenticatedUser(USER_A);
        ImportJobHistory historyA = new ImportJobHistory(
                TransactionSource.AMAZON, TriggerType.MANUAL, OffsetDateTime.now().minusMinutes(5));
        historyA.setTotalFiles(3);
        importJobHistoryRepository.save(historyA);

        setAuthenticatedUser(USER_B);
        ImportJobHistory historyB = new ImportJobHistory(
                TransactionSource.AMAZON, TriggerType.MANUAL, OffsetDateTime.now());
        historyB.setPrivacyLevel(PrivacyLevel.SHARED);
        historyB.setTotalFiles(99);
        importJobHistoryRepository.save(historyB);

        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/tasks/AMAZON/history")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].totalFiles").value(3));

        // User C has no job history records
        mockMvc.perform(get("/tasks/AMAZON/history")
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }
}
