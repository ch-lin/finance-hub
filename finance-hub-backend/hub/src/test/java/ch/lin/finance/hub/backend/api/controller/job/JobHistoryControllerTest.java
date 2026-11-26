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

import java.util.Collections;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.job.JobHistoryService;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobHistoryResult;
import ch.lin.finance.hub.backend.api.controller.job.dto.JobHistoryMapper;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
class JobHistoryControllerTest {

    @Mock
    private JobHistoryService jobHistoryService;

    @Mock
    @SuppressWarnings("unused")
    private JobHistoryMapper jobHistoryMapper;

    @InjectMocks
    private JobHistoryController jobHistoryController;

    private MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(jobHistoryController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("Should return job history page")
    void getJobHistory_ReturnsPage() throws Exception {
        Page<JobHistoryResult> page = new PageImpl<>(
                Objects.requireNonNull(Collections.<JobHistoryResult>emptyList()),
                PageRequest.of(0, 10), 0);

        when(jobHistoryService.getHistoryByTransactionSource(eq(TransactionSource.AMAZON), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/tasks/{transactionSource}/history", "AMAZON")
                .param("page", "0")
                .param("size", "10")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk());

        verify(jobHistoryService, times(1)).getHistoryByTransactionSource(eq(TransactionSource.AMAZON),
                any(Pageable.class));
    }
}
