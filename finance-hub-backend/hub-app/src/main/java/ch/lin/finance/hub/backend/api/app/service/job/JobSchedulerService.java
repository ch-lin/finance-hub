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

/**
 * Defines the contract for managing the lifecycle of automated background 
 * import jobs.
 * <p>
 * The scheduler is responsible for polling external data sources or processing 
 * queued files at regular intervals, ensuring the finance hub stays up-to-date 
 * automatically.
 */
public interface JobSchedulerService {

    /**
     * Iterates through all registered transaction sources and starts a scheduled
     * task for any source configured for auto-start.
     */
    void start();

    /**
     * Gracefully cancels all currently active scheduled polling tasks across 
     * all transaction sources.
     */
    void stop();

    /**
     * Determines whether any scheduled tasks are currently active.
     *
     * @return {@code true} if at least one background scheduler is active, {@code false} otherwise.
     */
    boolean isSchedulerRunning();
}
