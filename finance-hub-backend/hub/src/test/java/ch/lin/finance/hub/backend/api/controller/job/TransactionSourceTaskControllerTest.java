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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.app.service.job.JobOrchestrator;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileUploadProcessingService;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
class TransactionSourceTaskControllerTest {

    @Mock
    private JobOrchestrator jobOrchestrator;

    @Mock
    private FileUploadProcessingService fileUploadProcessingService;

    @InjectMocks
    private TransactionSourceTaskController transactionSourceTaskController;

    private MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transactionSourceTaskController).build();
    }

    @Test
    @DisplayName("processData: should start process when job is not running")
    void processData_JobNotRunning_StartsProcess() throws Exception {
        when(jobOrchestrator.isJobRunning(TransactionSource.AMAZON)).thenReturn(false);

        mockMvc.perform(post("/tasks/AMAZON/process")
                .param("dryRun", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("processing"))
                .andExpect(jsonPath("$.data.message")
                        .value("Process has been started in the background for transaction source: AMAZON"));

        verify(jobOrchestrator, times(1)).isJobRunning(TransactionSource.AMAZON);
        // Wait a bit for async execution, although runAsync might execute later.
        // We just ensure it returns 202 quickly. We don't verify CompletableFuture
        // directly in MockMvc easily.
    }

    @Test
    @DisplayName("processData: should return conflict when job is already running")
    void processData_JobRunning_ReturnsConflict() throws Exception {
        when(jobOrchestrator.isJobRunning(TransactionSource.AMAZON)).thenReturn(true);

        mockMvc.perform(post("/tasks/AMAZON/process"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("TASK_CONFLICT"))
                .andExpect(
                        jsonPath("$.data.message").value(
                                "Task is already in progress for transaction source: AMAZON"));

        verify(jobOrchestrator, times(1)).isJobRunning(TransactionSource.AMAZON);
    }

    @Test
    @DisplayName("getProcessStatus: should return running true")
    void getProcessStatus_ReturnsStatus() throws Exception {
        when(jobOrchestrator.isJobRunning(TransactionSource.CREDIT_CARD)).thenReturn(true);

        mockMvc.perform(get("/tasks/CREDIT_CARD/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRunning").value(true));

        verify(jobOrchestrator, times(1)).isJobRunning(TransactionSource.CREDIT_CARD);
    }

    @Test
    @DisplayName("uploadFile: should upload file and return result")
    void uploadFile_ProcessesFileSuccessfully() throws Exception {
        FileProcessingResult result = new FileProcessingResult("test.csv", true, null);
        // Just mock the returned result
        when(fileUploadProcessingService.process(eq(TransactionSource.AMAZON), any(MultipartFile.class),
                eq(true),
                eq(10)))
                .thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "test data".getBytes());

        mockMvc.perform(multipart("/tasks/AMAZON/upload")
                .file(file)
                .param("dryRun", "true")
                .param("timeoutSeconds", "10")
                .contentType(java.util.Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA)))
                .andExpect(status().isOk());

        verify(fileUploadProcessingService, times(1)).process(eq(TransactionSource.AMAZON),
                any(MultipartFile.class),
                eq(true), eq(10));
    }

    @Test
    @DisplayName("uploadFile: should upload file without timeout and dryRun false by default")
    void uploadFile_NoTimeout_Defaults() throws Exception {
        FileProcessingResult result = new FileProcessingResult("test.csv", false, null);
        when(fileUploadProcessingService.process(eq(TransactionSource.AMAZON), any(MultipartFile.class),
                eq(false),
                isNull()))
                .thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "test data".getBytes());

        mockMvc.perform(multipart("/tasks/AMAZON/upload")
                .file(file))
                .andExpect(status().isOk());

        verify(fileUploadProcessingService, times(1)).process(eq(TransactionSource.AMAZON),
                any(MultipartFile.class),
                eq(false), isNull());
    }
}
