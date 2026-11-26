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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ch.lin.finance.hub.backend.api.app.repository.job.ImportJobHistoryRepository;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobHistoryResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.ImportJobHistory;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "null", "unused" })
class JobHistoryServiceImplTest {

    @Mock
    private ImportJobHistoryRepository importJobHistoryRepository;

    @InjectMocks
    private JobHistoryServiceImpl jobHistoryService;

    private ImportJobHistory jobHistory;

    @BeforeEach
    void setUp() {
        jobHistory = new ImportJobHistory(TransactionSource.AMAZON, TriggerType.MANUAL, java.time.OffsetDateTime.now());
    }

    @Test
    void testStartJob() {
        when(importJobHistoryRepository.save(any(ImportJobHistory.class))).thenReturn(jobHistory);

        ImportJobHistory result = jobHistoryService.startJob(TransactionSource.AMAZON, TriggerType.MANUAL);

        assertNotNull(result);
        assertEquals(TransactionSource.AMAZON, result.getTransactionSource());
        verify(importJobHistoryRepository).save(any(ImportJobHistory.class));
    }

    @Test
    void testCompleteJob_Success() {
        jobHistoryService.completeJob(jobHistory, 10, 10, 0, 0, null);

        assertEquals(10, jobHistory.getTotalFiles());
        assertEquals("SUCCESS", jobHistory.getStatus());
        assertNotNull(jobHistory.getEndTime());
        verify(importJobHistoryRepository).save(jobHistory);
    }

    @Test
    void testCompleteJob_PartialSuccess_WithFailedFiles() {
        Map<String, String> failedFiles = Map.of("file1.csv", "Format error", "file2.csv", "Null error");

        jobHistoryService.completeJob(jobHistory, 10, 8, 0, 2, failedFiles);

        assertEquals("PARTIAL_SUCCESS", jobHistory.getStatus());
        assertEquals(2, jobHistory.getFailedFiles().size());
        verify(importJobHistoryRepository).save(jobHistory);
    }

    @Test
    void testCompleteJob_NullHistory() {
        jobHistoryService.completeJob(null, 10, 10, 0, 0, null);
        // Should return gracefully without exception
    }

    @Test
    void testFailJob() {
        jobHistoryService.failJob(jobHistory, "Unexpected error");

        assertEquals("FAILED", jobHistory.getStatus());
        assertEquals("Unexpected error", jobHistory.getErrorMessage());
        assertNotNull(jobHistory.getEndTime());
        verify(importJobHistoryRepository).save(jobHistory);
    }

    @Test
    void testFailJob_NullHistory() {
        jobHistoryService.failJob(null, "Error");
        // Should return gracefully
    }

    @Test
    void testGetHistoryByTransactionSource() {
        Pageable pageable = Pageable.unpaged();
        when(importJobHistoryRepository.findAllByTransactionSourceOrderByStartTimeDesc(TransactionSource.AMAZON,
                pageable))
                .thenReturn(Page.empty());

        Page<JobHistoryResult> result;
        result = jobHistoryService.getHistoryByTransactionSource(TransactionSource.AMAZON,
                pageable);

        assertNotNull(result);
        verify(importJobHistoryRepository).findAllByTransactionSourceOrderByStartTimeDesc(TransactionSource.AMAZON,
                pageable);
    }

    @Test
    void testCompleteJob_WithEmptyFailedFiles() {
        jobHistoryService.completeJob(jobHistory, 10, 10, 0, 0, java.util.Collections.emptyMap());

        assertEquals(10, jobHistory.getTotalFiles());
        assertEquals("SUCCESS", jobHistory.getStatus());
        assertEquals(0, jobHistory.getFailedFiles().size());
    }

    @Test
    void testCompleteJob_WithNullErrorMessage() {
        java.util.Map<String, String> failedFiles = new java.util.HashMap<>();
        failedFiles.put("file1.csv", null);

        jobHistoryService.completeJob(jobHistory, 1, 0, 0, 1, failedFiles);

        assertEquals("PARTIAL_SUCCESS", jobHistory.getStatus());
        assertEquals(1, jobHistory.getFailedFiles().size());

        // Use an iterator or stream to get the element since getFailedFiles might
        // return a Set or List
        ch.lin.finance.hub.backend.api.domain.model.history.ImportJobFailedFile failedFile = jobHistory.getFailedFiles()
                .iterator().next();
        assertEquals("Unknown error", failedFile.getErrorMessage());
    }
}
