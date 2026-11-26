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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

import static org.mockito.Mockito.when;

class FileProcessingOrchestratorTest {
    private FileProcessingStrategy mockStrategy;
    private FileProcessingOrchestrator orchestrator;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockStrategy = Mockito.mock(FileProcessingStrategy.class);
        when(mockStrategy.getSource()).thenReturn(TransactionSource.AMAZON);
        orchestrator = new FileProcessingOrchestrator(List.of(mockStrategy));
    }

    @Test
    void testProcessFile_Success() throws Exception {
        File file = new File("test.csv");
        FileProcessingResult expectedResult = new FileProcessingResult("success", false, null);
        when(mockStrategy.processFile(any(File.class), eq(false), eq("admin"))).thenReturn(expectedResult);

        FileProcessingResult result = orchestrator.processFile(TransactionSource.AMAZON, file, false);

        assertEquals("success", result.status());
        assertFalse(result.dryRun());
    }

    @Test
    void testProcessFile_NoStrategyFound() {
        File file = new File("test.csv");
        FileProcessingResult result = orchestrator.processFile(TransactionSource.CREDIT_CARD, file, false);

        assertEquals("error", result.status());
        assertTrue(result.data().toString().contains("No file processing strategy found"));
    }

    @Test
    void testProcessFile_StrategyThrowsException() throws Exception {
        File file = new File("test.csv");
        when(mockStrategy.processFile(any(File.class), eq(true), eq("admin")))
                .thenThrow(new RuntimeException("Parsing failed"));

        FileProcessingResult result = orchestrator.processFile(TransactionSource.AMAZON, file, true);

        assertEquals("error", result.status());
        assertEquals("Parsing failed", result.data());
        assertTrue(result.dryRun());
    }
}
