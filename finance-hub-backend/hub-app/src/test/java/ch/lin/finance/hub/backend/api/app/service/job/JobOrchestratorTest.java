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
package ch.lin.finance.hub.backend.api.app.service.job;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "null", "unused" })
class JobOrchestratorTest {

    @Mock
    private JobHistoryService historyService;

    @Mock
    private JobExecutor amazonExecutor;

    private JobOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        when(amazonExecutor.getSource()).thenReturn(TransactionSource.AMAZON);
        orchestrator = new JobOrchestratorImpl(historyService, List.of(amazonExecutor));
    }

    @Test
    void testGetSupportedSources() {
        assertTrue(orchestrator.getSupportedSources().contains(TransactionSource.AMAZON));
        assertFalse(orchestrator.getSupportedSources().contains(TransactionSource.CREDIT_CARD));
    }

    @Test
    void testIsJobRunning() {
        assertFalse(orchestrator.isJobRunning(TransactionSource.AMAZON));
    }

    @Test
    void testExecuteJob_NormalRun() throws Exception {
        JobResult result = new JobResult(10, 8, 0, 2, Map.of("f1", "err"));
        when(amazonExecutor.execute(false)).thenReturn(result);

        ImportJobHistory history = new ImportJobHistory(TransactionSource.AMAZON, TriggerType.MANUAL,
                java.time.OffsetDateTime.now());
        when(historyService.startJob(TransactionSource.AMAZON, TriggerType.MANUAL)).thenReturn(history);

        orchestrator.executeJob(TransactionSource.AMAZON, TriggerType.MANUAL, false);

        verify(historyService).startJob(TransactionSource.AMAZON, TriggerType.MANUAL);
        verify(amazonExecutor).execute(false);
        verify(historyService).completeJob(history, 10, 8, 0, 2, result.failedFiles());
        assertFalse(orchestrator.isJobRunning(TransactionSource.AMAZON));
    }

    @Test
    void testExecuteJob_DryRun() throws Exception {
        JobResult result = new JobResult(10, 10, 0, 0, null);
        when(amazonExecutor.execute(true)).thenReturn(result);

        orchestrator.executeJob(TransactionSource.AMAZON, TriggerType.MANUAL, true);

        verify(historyService, never()).startJob(any(), any());
        verify(amazonExecutor).execute(true);
        verify(historyService, never()).completeJob(any(), eq(10), eq(10), eq(0), eq(0), any());
        assertFalse(orchestrator.isJobRunning(TransactionSource.AMAZON));
    }

    @Test
    void testExecuteJob_NoExecutorFound() {
        orchestrator.executeJob(TransactionSource.CREDIT_CARD, TriggerType.MANUAL, false);

        verify(historyService, never()).startJob(any(), any());
        assertFalse(orchestrator.isJobRunning(TransactionSource.CREDIT_CARD));
    }

    @Test
    void testExecuteJob_ExceptionThrown() throws Exception {
        when(amazonExecutor.execute(false)).thenThrow(new RuntimeException("Test Exception"));

        ImportJobHistory history = new ImportJobHistory(TransactionSource.AMAZON, TriggerType.MANUAL,
                java.time.OffsetDateTime.now());
        when(historyService.startJob(TransactionSource.AMAZON, TriggerType.MANUAL)).thenReturn(history);

        orchestrator.executeJob(TransactionSource.AMAZON, TriggerType.MANUAL, false);

        verify(historyService).startJob(TransactionSource.AMAZON, TriggerType.MANUAL);
        verify(historyService).failJob(history, "Test Exception");
        assertFalse(orchestrator.isJobRunning(TransactionSource.AMAZON));
    }

    @Test
    void testExecuteJob_ExceptionThrown_DryRun() throws Exception {
        when(amazonExecutor.execute(true)).thenThrow(new RuntimeException("Test Exception"));

        orchestrator.executeJob(TransactionSource.AMAZON, TriggerType.MANUAL, true);

        verify(historyService, never()).startJob(any(), any());
        verify(historyService, never()).failJob(any(), any());
        assertFalse(orchestrator.isJobRunning(TransactionSource.AMAZON));
    }

    @Test
    void testExecuteJob_AlreadyRunning() {
        // Manually set the lock to true
        java.util.concurrent.atomic.AtomicBoolean lock = new java.util.concurrent.atomic.AtomicBoolean(true);
        @SuppressWarnings("unchecked")
        Map<TransactionSource, java.util.concurrent.atomic.AtomicBoolean> locks = (Map<TransactionSource, java.util.concurrent.atomic.AtomicBoolean>) org.springframework.test.util.ReflectionTestUtils
                .getField(orchestrator, "runLocks");
        locks.put(TransactionSource.AMAZON, lock);

        orchestrator.executeJob(TransactionSource.AMAZON, TriggerType.MANUAL, false);

        // Verify that it skips execution and doesn't call historyService.startJob or
        // amazonExecutor.execute
        verify(historyService, never()).startJob(any(), any());

        try {
            verify(amazonExecutor, never()).execute(any(Boolean.class));
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail("Should not throw exception here");
        }
    }

    @Test
    void testExecuteJob_ExceptionThrown_NullMessage() throws Exception {
        when(amazonExecutor.execute(false)).thenThrow(new RuntimeException());

        ImportJobHistory history = new ImportJobHistory(TransactionSource.AMAZON, TriggerType.MANUAL,
                java.time.OffsetDateTime.now());
        when(historyService.startJob(TransactionSource.AMAZON, TriggerType.MANUAL)).thenReturn(history);

        orchestrator.executeJob(TransactionSource.AMAZON, TriggerType.MANUAL, false);

        verify(historyService).startJob(TransactionSource.AMAZON, TriggerType.MANUAL);
        verify(historyService).failJob(history, "RuntimeException");
        assertFalse(orchestrator.isJobRunning(TransactionSource.AMAZON));
    }
}
