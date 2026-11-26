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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.job.JobHistoryService;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobHistoryResult;
import ch.lin.finance.hub.backend.api.controller.job.dto.JobHistoryMapper;
import ch.lin.finance.hub.backend.api.controller.job.dto.JobHistoryResponse;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.platform.api.ApiResponse;

/**
 * REST controller for retrieving job execution history.
 */
@RestController
@RequestMapping("/tasks")
public class JobHistoryController {

    /** Logger for this controller. */
    private static final Logger logger = LoggerFactory.getLogger(JobHistoryController.class);

    /** Service to retrieve job execution histories. */
    private final JobHistoryService jobHistoryService;

    /** Mapper for job history responses. */
    private final JobHistoryMapper jobHistoryMapper;

    /**
     * Constructs a new JobHistoryController.
     *
     * @param jobHistoryService The service to retrieve job histories.
     * @param jobHistoryMapper  The mapper to convert results to responses.
     */
    public JobHistoryController(JobHistoryService jobHistoryService, JobHistoryMapper jobHistoryMapper) {
        this.jobHistoryService = jobHistoryService;
        this.jobHistoryMapper = jobHistoryMapper;
    }

    /**
     * Retrieves the paginated job history for a given transaction source.
     *
     * @param transactionSource The integration source (e.g., AMAZON).
     * @param page              The page number to retrieve (0-based).
     * @param size              The number of records per page.
     * @return A {@link ResponseEntity} containing a paginated list of
     *         {@link JobHistoryResponse}.
     */
    @GetMapping(value = "/{transactionSource}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<JobHistoryResponse>>> getJobHistory(
            @PathVariable("transactionSource") TransactionSource transactionSource,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.debug("Fetching job history for transaction source: {}, page: {}, size: {}", transactionSource, page,
                size);
        Pageable pageable = PageRequest.of(page, size);
        Page<JobHistoryResult> historyPage = jobHistoryService.getHistoryByTransactionSource(transactionSource,
                pageable);
        Page<JobHistoryResponse> responsePage = historyPage.map(jobHistoryMapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success(responsePage));
    }
}
