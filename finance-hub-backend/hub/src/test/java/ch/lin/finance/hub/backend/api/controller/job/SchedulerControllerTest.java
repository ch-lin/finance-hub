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

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.job.JobSchedulerService;

@ExtendWith(MockitoExtension.class)
class SchedulerControllerTest {

    @Mock
    private JobSchedulerService jobSchedulerService;

    @InjectMocks
    private SchedulerController schedulerController;

    private MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(schedulerController).build();
    }

    @Test
    @DisplayName("Should start scheduler successfully")
    void startScheduler_Success() throws Exception {
        mockMvc.perform(post("/tasks/scheduler/start")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Global schedulers started."));

        verify(jobSchedulerService, times(1)).start();
    }

    @Test
    @DisplayName("Should stop scheduler successfully")
    void stopScheduler_Success() throws Exception {
        mockMvc.perform(post("/tasks/scheduler/stop")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Global schedulers stopped."));

        verify(jobSchedulerService, times(1)).stop();
    }

    @Test
    @DisplayName("Should return scheduler status running = true")
    void getSchedulerStatus_RunningTrue() throws Exception {
        when(jobSchedulerService.isSchedulerRunning()).thenReturn(true);

        mockMvc.perform(get("/tasks/scheduler/status")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running").value(true));

        verify(jobSchedulerService, times(1)).isSchedulerRunning();
    }

    @Test
    @DisplayName("Should return scheduler status running = false")
    void getSchedulerStatus_RunningFalse() throws Exception {
        when(jobSchedulerService.isSchedulerRunning()).thenReturn(false);

        mockMvc.perform(get("/tasks/scheduler/status")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running").value(false));

        verify(jobSchedulerService, times(1)).isSchedulerRunning();
    }
}
