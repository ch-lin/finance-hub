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
package ch.lin.finance.hub.backend.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for application health checks.
 * <p>
 * This controller provides a simple endpoint to verify the operational status
 * of the service. It is primarily used as a liveness probe by
 * Docker/Kubernetes.
 */
@RestController
@RequestMapping("/health")
public class HealthCheckController {

    /**
     * Provides a health check endpoint for the main backend service.
     * <p>
     * This endpoint's reachability implicitly confirms the Spring Boot
     * application context has started successfully and is accepting HTTP
     * requests.
     *
     * @return A {@link ResponseEntity} with an HTTP 200 OK status and a simple
     *         "OK" string body.
     *
     *         <pre>{@code curl -X GET http://localhost:8080/health}</pre>
     */
    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
