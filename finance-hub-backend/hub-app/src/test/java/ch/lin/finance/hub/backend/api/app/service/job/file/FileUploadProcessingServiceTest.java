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
package ch.lin.finance.hub.backend.api.app.service.job.file;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.app.service.config.system.FinanceHubConfigService;
import ch.lin.finance.hub.backend.api.common.exception.TaskProcessingException;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "null", "unused" })
class FileUploadProcessingServiceTest {

    @Mock
    private FileProcessingOrchestrator fileProcessingOrchestrator;

    @Mock
    private FinanceHubConfigService financeHubConfigService;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private FileUploadProcessingServiceImpl fileUploadProcessingService;

    private FinanceHubConfig config;

    @BeforeEach
    void setUp() {
        config = org.mockito.Mockito.mock(FinanceHubConfig.class);
        org.mockito.Mockito.lenient().when(config.getFileProcessingTimeoutSeconds()).thenReturn(10);
        when(financeHubConfigService.getResolvedConfig(null)).thenReturn(config);
    }

    @Test
    void testProcess_Success() throws Exception {
        FileProcessingResult expectedResult = new FileProcessingResult("success", false, null);
        when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), eq(false)))
                .thenReturn(expectedResult);

        FileProcessingResult result = fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile,
                false, null);

        assertNotNull(result);
        assertEquals("success", result.status());
    }

    @Test
    void testProcess_TimeoutOverride() throws Exception {
        FileProcessingResult expectedResult = new FileProcessingResult("success", false, null);
        when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), eq(false)))
                .thenReturn(expectedResult);

        FileProcessingResult result = fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile,
                false, 5);

        assertNotNull(result);
        assertEquals("success", result.status());
    }

    @Test
    void testProcess_IOException() throws Exception {
        doThrow(new IOException("Disk error")).when(multipartFile).transferTo(any(File.class));

        TaskProcessingException exception = assertThrows(TaskProcessingException.class, () -> {
            fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile, false, null);
        });

        assertEquals("PROCESSING_ERROR", exception.getErrorCode());
    }

    @Test
    void testProcess_TimeoutException() throws Exception {
        when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    Thread.sleep(2000); // Sleep longer than timeout
                    return new FileProcessingResult("success", false, null);
                });

        TaskProcessingException exception = assertThrows(TaskProcessingException.class, () -> {
            fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile, false, 1);
        });

        assertEquals("TIMEOUT", exception.getErrorCode());
    }

    @Test
    void testProcess_TimeoutOverrideInvalid_FallsBackToConfig() throws Exception {
        FileProcessingResult expectedResult = new FileProcessingResult("success", false, null);
        when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), eq(false)))
                .thenReturn(expectedResult);

        FileProcessingResult result = fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile,
                false, 0);

        assertNotNull(result);
        assertEquals("success", result.status());
    }

    @Test
    void testProcess_ConfigTimeoutNull_FallsBackToDefault() throws Exception {
        when(config.getFileProcessingTimeoutSeconds()).thenReturn(null);
        FileProcessingResult expectedResult = new FileProcessingResult("success", false, null);
        when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), eq(false)))
                .thenReturn(expectedResult);

        FileProcessingResult result = fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile,
                false, null);

        assertNotNull(result);
        assertEquals("success", result.status());
    }

    @Test
    void testProcess_InterruptedException() throws Exception {
        Thread testThread = Thread.currentThread();
        when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    testThread.interrupt();
                    Thread.sleep(5000);
                    return new FileProcessingResult("success", false, null);
                });

        TaskProcessingException exception = assertThrows(TaskProcessingException.class, () -> {
            fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile, false, null);
        });

        assertEquals("INTERRUPTED", exception.getErrorCode());
        // Verify interrupt flag is set
        org.junit.jupiter.api.Assertions.assertTrue(Thread.interrupted()); // This also clears the flag
    }

    @Test
    void testProcess_FailedToDeleteTempFile() throws Exception {
        File mockTempFile = org.mockito.Mockito.mock(File.class);
        when(mockTempFile.exists()).thenReturn(true);
        when(mockTempFile.delete()).thenReturn(false);
        when(mockTempFile.getAbsolutePath()).thenReturn("/tmp/mockfile");

        try (org.mockito.MockedStatic<File> fileMockedStatic = org.mockito.Mockito.mockStatic(File.class)) {
            fileMockedStatic.when(() -> File.createTempFile(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString())).thenReturn(mockTempFile);

            FileProcessingResult expectedResult = new FileProcessingResult("success", false, null);
            when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), eq(false)))
                    .thenReturn(expectedResult);

            FileProcessingResult result = fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile,
                    false, null);

            assertNotNull(result);
            assertEquals("success", result.status());
        }
    }

    @Test
    void testProcess_TempFileNull_WhenExceptionBeforeCreation() {
        when(financeHubConfigService.getResolvedConfig(null)).thenThrow(new RuntimeException("Config error"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile, false, null);
        });
        assertEquals("Config error", exception.getMessage());
    }

    @Test
    void testProcess_TempFileDoesNotExistInFinally() throws Exception {
        File mockTempFile = org.mockito.Mockito.mock(File.class);
        when(mockTempFile.exists()).thenReturn(false);

        try (org.mockito.MockedStatic<File> fileMockedStatic = org.mockito.Mockito.mockStatic(File.class)) {
            fileMockedStatic.when(() -> File.createTempFile(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString())).thenReturn(mockTempFile);

            FileProcessingResult expectedResult = new FileProcessingResult("success", false, null);
            when(fileProcessingOrchestrator.processFile(eq(TransactionSource.AMAZON), any(File.class), eq(false)))
                    .thenReturn(expectedResult);

            FileProcessingResult result = fileUploadProcessingService.process(TransactionSource.AMAZON, multipartFile,
                    false, null);

            assertNotNull(result);
            assertEquals("success", result.status());
        }
    }
}
