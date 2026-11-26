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

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * The contract that transaction sources must implement to support single-file
 * processing.
 * <p>
 * This Strategy pattern interface allows the system to seamlessly route
 * different file types (e.g., Credit Card CSVs vs. Amazon Order CSVs) to their
 * respective processing logic without hardcoding conditionals.
 */
public interface FileProcessingStrategy {

    /**
     * Identifies which transaction source this specific strategy handles.
     * <p>
     * Used by the orchestrator during bean initialization to register the strategy.
     *
     * @return The {@link TransactionSource} (e.g., AMAZON, CREDIT_CARD) supported
     *         by this strategy.
     */
    TransactionSource getSource();

    /**
     * Processes a single file corresponding to this strategy's transaction source.
     * <p>
     * The method parses the file, converts its contents into domain entities, and
     * optionally
     * persists them to the database if {@code dryRun} is false.
     *
     * @param tempFile    The physical file to be parsed and processed.
     * @param dryRun      If {@code true}, the file is parsed and validated, but no
     *                    changes are persisted to the database.
     * @param ownerUserId The ID of the user owning the transactions.
     * @return A {@link FileProcessingResult} containing the outcome status, dry run
     *         flag, and extracted data.
     * @throws Exception If any error occurs during file reading, parsing, or
     *                   database saving.
     */
    FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception;
}
