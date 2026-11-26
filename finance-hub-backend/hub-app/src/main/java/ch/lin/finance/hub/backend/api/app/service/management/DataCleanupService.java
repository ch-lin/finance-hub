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
package ch.lin.finance.hub.backend.api.app.service.management;

/**
 * Service dedicated to purging data and resetting the database state.
 * <p>
 * Intended for use in testing environments or when a user wants to perform a 
 * complete factory reset of their transactional data without losing integration 
 * configurations.
 */
public interface DataCleanupService {

    /**
     * Cleans up all transactional data (credit cards, amazon orders, links, and
     * history) and resets their auto-increment sequences. 
     * <p>
     * Note: Configuration data (e.g., FinanceHubConfig, TransactionSourceConfig) 
     * is completely preserved during this operation.
     */
    void cleanup();

}
