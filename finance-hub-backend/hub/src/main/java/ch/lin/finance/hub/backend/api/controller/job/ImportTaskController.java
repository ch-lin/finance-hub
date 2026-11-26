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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.job.JobOrchestrator;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;
import ch.lin.platform.api.ApiResponse;

@RestController
@RequestMapping("/import")
public class ImportTaskController {

    private static final Logger logger = LoggerFactory.getLogger(ImportTaskController.class);

    private final JobOrchestrator jobOrchestrator;

    // In-memory tracker for onboarding task statuses (for polling)
    private final Map<String, Map<TransactionSource, String>> userTaskProgress = new ConcurrentHashMap<>();

    public ImportTaskController(JobOrchestrator jobOrchestrator) {
        this.jobOrchestrator = jobOrchestrator;
    }

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<Map<String, String>>> importTasks(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody List<TransactionSource> sources) {

        String userId = SecurityUtil.getUserId(jwt);

        if (sources == null || sources.isEmpty()) {
            logger.info("No tasks to import for user: {}", userId);
            return ResponseEntity.accepted().body(ApiResponse.success(Map.of("status", "completed")));
        }

        logger.info("Starting import tasks for user: {} with sources: {}", userId, sources);

        // Initialize progress tracker
        Map<TransactionSource, String> progress = new ConcurrentHashMap<>();
        for (TransactionSource source : sources) {
            progress.put(source, "PENDING");
        }
        userTaskProgress.put(userId, progress);

        // Run all jobs asynchronously
        CompletableFuture<?>[] futures = sources.stream()
                .map(source -> CompletableFuture.runAsync(() -> {
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    // Create an explicitly authenticated token with the userId as the principal
                    Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                    context.setAuthentication(auth);
                    SecurityContextHolder.setContext(context);

                    try {
                        logger.info("Background thread for source {} - Context: {}, Auth: {}",
                                source, SecurityContextHolder.getContext(), auth);
                        progress.put(source, "RUNNING");
                        jobOrchestrator.executeJob(source, TriggerType.MANUAL, false);
                        progress.put(source, "COMPLETED");
                    } catch (Exception e) {
                        logger.error("Failed to execute job for source: {}", source, e);
                        progress.put(source, "FAILED");
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).whenComplete((res, ex) -> {
            logger.info("All import tasks completed for user: {}", userId);
        });

        return ResponseEntity.accepted().body(ApiResponse.success(Map.of("status", "processing")));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTasksStatus(@AuthenticationPrincipal Jwt jwt) {
        String userId = SecurityUtil.getUserId(jwt);
        Map<TransactionSource, String> progress = userTaskProgress.getOrDefault(userId, Map.of());

        boolean isCompleted = false;
        if (!progress.isEmpty()) {
            isCompleted = progress.values().stream()
                    .allMatch(status -> "COMPLETED".equals(status) || "FAILED".equals(status));
        }

        Map<String, Object> response = Map.of(
                "tasks", progress,
                "isAllCompleted", isCompleted);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
