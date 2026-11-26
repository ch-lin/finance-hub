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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.job.JobSchedulerService;
import ch.lin.platform.api.ApiResponse;

/**
 * REST controller for managing the global task scheduler.
 * <p>
 * This controller provides endpoints to start, stop, and check the status of
 * the background jobs responsible for auto-processing financial data across all
 * configured platform sources (e.g., Amazon, Rakuten).
 */
@RestController
@RequestMapping("/tasks/scheduler")
public class SchedulerController {

    /** Logger for this controller. */
    private static final Logger logger = LoggerFactory.getLogger(SchedulerController.class);

    /** Service responsible for managing job schedules. */
    private final JobSchedulerService jobSchedulerService;

    /**
     * Constructs a new SchedulerController.
     *
     * @param jobSchedulerService The service used to manage job schedules.
     */
    public SchedulerController(JobSchedulerService jobSchedulerService) {
        this.jobSchedulerService = jobSchedulerService;
    }

    /**
     * Starts the scheduled background jobs for auto-processing financial data.
     * <p>
     * This triggers the scheduler for all enabled integration sources. This
     * triggers the scheduler for all enabled platform sources.
     *
     * @return A {@link ResponseEntity} with a success message.
     *         <p>
     *         Example cURL request:
     * 
     *         <pre>{@code curl -X POST http://localhost:8085/tasks/scheduler/start}</pre>
     */
    @PostMapping(value = "/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<String>> startScheduler() {
        logger.info("Received request to start the global task schedulers.");
        jobSchedulerService.start();
        return ResponseEntity.ok(ApiResponse.success("Global schedulers started."));
    }

    /**
     * Stops the scheduled background jobs.
     * <p>
     * This halts the scheduler for all active integration sources.
     *
     * @return A {@link ResponseEntity} with a success message.
     *         <p>
     *         Example cURL request:
     * 
     *         <pre>{@code curl -X POST http://localhost:8085/tasks/scheduler/stop}</pre>
     */
    @PostMapping(value = "/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<String>> stopScheduler() {
        logger.info("Received request to stop the global task schedulers.");
        jobSchedulerService.stop();
        return ResponseEntity.ok(ApiResponse.success("Global schedulers stopped."));
    }

    /**
     * Retrieves the current status of the global task scheduler.
     *
     * @return A {@link ResponseEntity} containing a map with a boolean flag
     *         indicating if any scheduler is currently running.
     *         <p>
     *         Example cURL request:
     * 
     *         <pre>{@code curl -X GET http://localhost:8085/tasks/scheduler/status}</pre>
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getSchedulerStatus() {
        logger.debug("Checking global scheduler running status.");
        boolean running = jobSchedulerService.isSchedulerRunning();
        return ResponseEntity.ok(ApiResponse.success(Map.of("running", running)));
    }
}
