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
package ch.lin.finance.hub.backend.api.controller.management;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.management.DataCleanupService;

/**
 * REST controller for managing data cleanup tasks.
 */
@RestController
@RequestMapping("/tasks")
public class DataCleanupController {

    /** Service to perform data cleanup operations. */
    private final DataCleanupService dataCleanupService;

    /**
     * Constructs a new DataCleanupController.
     *
     * @param dataCleanupService The service to perform data cleanup operations.
     */
    public DataCleanupController(DataCleanupService dataCleanupService) {
        this.dataCleanupService = dataCleanupService;
    }

    /**
     * Deletes all transactional data processed by the Finance Hub.
     *
     * @return A {@link ResponseEntity} with an HTTP 204 No Content status upon
     *         successful cleanup.
     */
    @DeleteMapping("/deletion")
    public ResponseEntity<Void> cleanUp() {
        dataCleanupService.cleanup();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
