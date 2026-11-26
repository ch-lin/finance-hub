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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.RakutenOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Job execution service for Rakuten-related data imports.
 * <p>
 * This class implements {@link JobExecutor} (for batch directory processing)
 * and {@link FileProcessingStrategy} (for single-file direct processing).
 */
@Service
public class RakutenJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(RakutenJobServiceImpl.class);

    private final String importPath;
    private final RakutenOrderRepository rakutenOrderRepository;
    private final RakutenOrderParser rakutenOrderParser;
    private final TransactionTemplate transactionTemplate;

    public RakutenJobServiceImpl(
            RakutenOrderRepository rakutenOrderRepository,
            RakutenOrderParser rakutenOrderParser,
            TransactionTemplate transactionTemplate,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.rakutenOrderRepository = rakutenOrderRepository;
        this.rakutenOrderParser = rakutenOrderParser;
        this.transactionTemplate = transactionTemplate;
        this.importPath = basePath + "/rakuten";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.RAKUTEN;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Starting Rakuten order processing. Dry run: {}, Input path: {}/source", dryRun, importPath);

        List<File> files = ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils
                .findHtmlFiles(importPath + "/source");

        if (files.isEmpty()) {
            logger.warn("No HTML files found in the source directory: {}/source", importPath);
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        List<String> needsReviewFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try {
                RakutenOrder parsedOrder = rakutenOrderParser.parse(file);
                RakutenOrder finalOrderToSave;

                if (!dryRun) {
                    finalOrderToSave = transactionTemplate.execute(status -> {
                        Optional<RakutenOrder> existingOpt = rakutenOrderRepository
                                .findByOrderId(parsedOrder.getOrderId());
                        if (existingOpt.isPresent()) {
                            RakutenOrder existingOrder = existingOpt.get();
                            existingOrder.merge(parsedOrder);
                            logger.info("Updating existing Rakuten order: {}", parsedOrder.getOrderId());
                            return rakutenOrderRepository.save(existingOrder);
                        } else {
                            logger.info("Saving new Rakuten order: {}", parsedOrder.getOrderId());
                            return rakutenOrderRepository.save(parsedOrder);
                        }
                    });
                } else {
                    finalOrderToSave = parsedOrder;
                    logger.info("DRY RUN - Parsed Rakuten order: {}", parsedOrder.getOrderId());
                }

                String year = ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils.extractYear(
                        finalOrderToSave != null ? finalOrderToSave.getOrderDate() : null, file);

                if (finalOrderToSave != null && Boolean.TRUE.equals(finalOrderToSave.getNeedsReview())) {
                    if (!dryRun) {
                        ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils.moveFileAndResources(
                                file,
                                importPath, "archive/needs_review", year);
                    }
                    needsReviewFiles.add(file.getName());
                } else {
                    if (!dryRun) {
                        ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils.moveFileAndResources(
                                file,
                                importPath, "archive/success", year);
                    }
                    successFiles.add(file.getName());
                }
            } catch (Exception e) {
                logger.error("Hard fail parsing file {}: {}", file.getName(), e.getMessage());
                String year = ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils
                        .extractYear(null, file);
                if (!dryRun) {
                    ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils.moveFileAndResources(
                            file, importPath,
                            "archive/failed", year);
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }

        return new JobResult(files.size(), successFiles.size(), needsReviewFiles.size(), failedFiles.size(),
                failedFiles);
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);

        RakutenOrder parsedOrder = rakutenOrderParser.parse(tempFile);
        RakutenOrder finalOrderToSave;

        if (!dryRun) {
            finalOrderToSave = transactionTemplate.execute(status -> {
                Optional<RakutenOrder> existingOpt = rakutenOrderRepository.findByOrderId(parsedOrder.getOrderId());
                if (existingOpt.isPresent()) {
                    RakutenOrder existingOrder = existingOpt.get();
                    existingOrder.merge(parsedOrder);
                    logger.info("Updating existing Rakuten order: {}", parsedOrder.getOrderId());
                    return rakutenOrderRepository.save(existingOrder);
                } else {
                    logger.info("Saving new Rakuten order: {}", parsedOrder.getOrderId());
                    return rakutenOrderRepository.save(parsedOrder);
                }
            });
        } else {
            finalOrderToSave = parsedOrder;
            logger.info("DRY RUN - Parsed Rakuten order: {}", parsedOrder.getOrderId());
        }

        return new FileProcessingResult("success", dryRun, finalOrderToSave);
    }
}
