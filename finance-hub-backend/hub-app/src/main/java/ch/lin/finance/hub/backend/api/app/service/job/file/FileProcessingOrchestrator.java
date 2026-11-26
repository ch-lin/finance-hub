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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Orchestrator for handling incoming single-file processing requests.
 * <p>
 * This class acts as a central router. It maintains a registry of all available
 * {@link FileProcessingStrategy} implementations and delegates the processing
 * of
 * a file to the correct strategy based on the provided
 * {@link TransactionSource}.
 */
@Service
public class FileProcessingOrchestrator {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(FileProcessingOrchestrator.class);

    /**
     * A thread-safe map associating a {@link TransactionSource} with its
     * corresponding
     * {@link FileProcessingStrategy}.
     */
    private final Map<TransactionSource, FileProcessingStrategy> strategyMap = new ConcurrentHashMap<>();

    /**
     * Constructs the orchestrator and dynamically registers all available
     * strategies.
     *
     * @param strategies A list of all beans implementing
     *                   {@link FileProcessingStrategy} injected by Spring.
     */
    public FileProcessingOrchestrator(List<FileProcessingStrategy> strategies) {
        for (FileProcessingStrategy strategy : strategies) {
            strategyMap.put(strategy.getSource(), strategy);
        }
    }

    /**
     * Routes the given file to the appropriate processing strategy.
     * <p>
     * If no matching strategy is found for the specified source, or if the strategy
     * throws
     * an exception during processing, an error {@link FileProcessingResult} is
     * returned
     * without propagating the exception to the caller.
     *
     * @param transactionSource The origin of the file data (e.g., AMAZON,
     *                          CREDIT_CARD).
     * @param file              The physical file to process.
     * @param dryRun            If {@code true}, the file is processed but data is
     *                          not saved to the database.
     * @return A {@link FileProcessingResult} detailing the success or error state
     *         of the operation.
     */
    public FileProcessingResult processFile(TransactionSource transactionSource, File file, boolean dryRun) {
        FileProcessingStrategy strategy = strategyMap.get(transactionSource);
        if (strategy == null) {
            String errorMessage = "No file processing strategy found for transaction source: " + transactionSource;
            logger.error(errorMessage);
            return new FileProcessingResult("error", dryRun, errorMessage);
        }

        try {
            return strategy.processFile(file, dryRun, "admin");
        } catch (Exception e) {
            logger.error("Failed to process file for transaction source {}", transactionSource, e);
            return new FileProcessingResult("error", dryRun, e.getMessage());
        }
    }
}
