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
package ch.lin.finance.hub.backend.api.app.service.accounting.creditcard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardStatementRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.common.exception.CsvRowsFailedException;
import ch.lin.finance.hub.backend.api.common.exception.UnsupportedCsvFormatException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Core orchestration service for handling credit card data ingestion.
 * <p>
 * This class coordinates the process of scanning import directories, selecting
 * the
 * correct parser via {@link CreditCardParserFactory}, parsing CSV files,
 * handling
 * parse errors (including generating error logs), and persisting results via
 * {@link CreditCardStatementRepository}.
 * <p>
 * Implements {@link JobExecutor} for batch processing and
 * {@link FileProcessingStrategy}
 * for on-demand single-file processing.
 */
@Service
public class CreditCardServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CreditCardServiceImpl.class);

    /**
     * Base path where credit card statement files are stored for processing.
     */
    private final String importPath;

    /**
     * Factory to retrieve the correct parser for a specific credit card file.
     */
    private final CreditCardParserFactory parserFactory;

    /**
     * Repository to persist parsed credit card statements.
     */
    private final CreditCardStatementRepository statementRepository;

    /**
     * Constructs the CreditCardServiceImpl.
     *
     * @param parserFactory       Factory to dynamically obtain the correct parser.
     * @param statementRepository Repository for saving parsed credit card
     *                            statements.
     * @param basePath            Base path where import files are located.
     */
    public CreditCardServiceImpl(
            CreditCardParserFactory parserFactory,
            CreditCardStatementRepository statementRepository,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.parserFactory = parserFactory;
        this.statementRepository = statementRepository;
        this.importPath = basePath + "/creditcard";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionSource getSource() {
        // Ensure CREDIT_CARD is added to your TransactionSource enum
        return TransactionSource.CREDIT_CARD;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Scans the source directory recursively for CSV files, delegates them to the
     * corresponding parser, and moves them to success/failed/unsupported archive
     * directories based on the outcome. It also generates an error log CSV if there
     * are unparseable rows.
     */
    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Starting Credit Card processing. Dry run: {}, Input path: {}", dryRun, importPath);

        // Restrict the scanning source to the "source" directory under the import path
        File sourceDirectory = new File(importPath, "source");
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
            logger.info("Source directory does not exist. Creating it: {}", sourceDirectory.getAbsolutePath());
            sourceDirectory.mkdirs();
            return new JobResult(0, 0, 0, 0, null);
        }

        List<File> files;
        // Use Files.walk to recursively scan the source directory and all its
        // subdirectories
        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            files = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .map(p -> p.toFile())
                    .collect(Collectors.toList());
        }

        if (files.isEmpty()) {
            logger.warn("No CSV files found in the source directory or its subdirectories: {}",
                    sourceDirectory.getAbsolutePath());
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            try {
                CreditCardParser parser = parserFactory.getParser(file);
                CreditCardParseResult parseResult = parser.parse(file);
                CreditCardStatement statement = parseResult.getStatement();

                if (!dryRun) {
                    // Assuming CreditCardStatement has a method to retrieve the card or bank name
                    // (e.g., getCardName())
                    // Adjust the method name according to your actual domain model if there's no
                    // such getter
                    String rawCardName = (statement != null && statement.getCardName() != null)
                            ? statement.getCardName()
                            : "unknown_card";
                    // Filter out characters that are not allowed in OS directory names to avoid
                    // path creation errors
                    String safeFolderName = rawCardName.replaceAll("[\\\\/:*?\"<>|]", "_");

                    if (!parseResult.getFailedRows().isEmpty()) {
                        logger.warn("File {} contains {} failed rows, moving to failed folder.", file.getName(),
                                parseResult.getFailedRows().size());
                        String failedDir = "archive/failed/" + safeFolderName;
                        ch.lin.finance.hub.backend.api.app.service.job.file.utils.CsvArchiveUtils.writeErrorLog(file,
                                importPath,
                                failedDir, parseResult.getFailedRows(), timestamp,
                                parseResult.getCharset());
                        ch.lin.finance.hub.backend.api.app.service.job.file.utils.CsvArchiveUtils.moveFile(file,
                                importPath,
                                failedDir,
                                timestamp);
                        failedFiles.put(file.getName(),
                                "Parsed with " + parseResult.getFailedRows().size() + " failed rows");
                    } else {
                        if (statement != null) {
                            saveOrMergeStatement(statement);
                        }
                        ch.lin.finance.hub.backend.api.app.service.job.file.utils.CsvArchiveUtils.moveFile(file,
                                importPath,
                                "archive/success/" + safeFolderName, timestamp);
                    }
                } else {
                    logger.info("DRY RUN - Parsed {} transactions from file {}: \n{}",
                            statement.getTransactions().size(), file.getName(), statement.getTransactions());
                    if (!parseResult.getFailedRows().isEmpty()) {
                        logger.warn("DRY RUN - File {} has failed rows: {}", file.getName(),
                                parseResult.getFailedRows());
                    }
                }
                if (parseResult.getFailedRows().isEmpty()) {
                    successFiles.add(file.getName());
                }

            } catch (UnsupportedCsvFormatException e) {
                logger.info("Skipping file {} due to unsupported format: {}", file.getName(), e.getMessage());
                if (!dryRun) {
                    try {
                        ch.lin.finance.hub.backend.api.app.service.job.file.utils.CsvArchiveUtils.moveFile(file,
                                importPath,
                                "archive/unsupported", timestamp);
                    } catch (IOException moveEx) {
                        logger.error("Could not move unsupported file {}: {}", file.getName(), moveEx.getMessage());
                    }
                }
                failedFiles.put(file.getName(), "Unsupported format");
            } catch (Exception e) {
                logger.error("Hard fail parsing file {}: {}", file.getName(), e.getMessage(), e);
                if (!dryRun) {
                    try {
                        ch.lin.finance.hub.backend.api.app.service.job.file.utils.CsvArchiveUtils.moveFile(file,
                                importPath,
                                "archive/failed/unknown_card", timestamp);
                    } catch (IOException moveEx) {
                        logger.error("Could not move failed file {}: {}", file.getName(), moveEx.getMessage());
                    }
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }

        if (!dryRun) {
            ch.lin.finance.hub.backend.api.app.service.job.file.utils.CsvArchiveUtils
                    .cleanupEmptyDirectories(sourceDirectory);
        }

        return new JobResult(files.size(), successFiles.size(), 0, failedFiles.size(), failedFiles);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Immediately processes a single file, throwing a
     * {@link CsvRowsFailedException}
     * if any row parsing fails, rather than generating a secondary error file.
     */
    @Transactional
    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);

        CreditCardParser parser = parserFactory.getParser(tempFile);
        CreditCardParseResult parseResult = parser.parse(tempFile);
        CreditCardStatement statement = parseResult.getStatement();

        if (!parseResult.getFailedRows().isEmpty()) {
            throw new CsvRowsFailedException(
                    "File '" + tempFile.getName() + "' contains " + parseResult.getFailedRows().size()
                            + " failed rows. First failed row: " + parseResult.getFailedRows().get(0),
                    parseResult.getFailedRows());
        }

        CreditCardStatement statementToReturn = statement;
        if (!dryRun) {
            statementToReturn = saveOrMergeStatement(Objects.requireNonNull(statement));
        } else {
            logger.info("DRY RUN - Processed {} transactions: \n{}", statement.getTransactions().size(),
                    statement.getTransactions());
        }

        return new FileProcessingResult("success", dryRun, statementToReturn.getTransactions().stream()
                .map(CreditCardTransactionResult::fromEntity)
                .toList());
    }

    private CreditCardStatement saveOrMergeStatement(CreditCardStatement statement) {
        if (statement == null) {
            return null;
        }
        Optional<CreditCardStatement> existingOpt = statementRepository
                .findByCardNameAndBillingMonth(statement.getCardName(), statement.getBillingMonth());

        if (existingOpt.isPresent()) {
            CreditCardStatement existing = existingOpt.get();
            existing.merge(statement);
            statementRepository.save(existing);
            return existing;
        } else {
            statementRepository.save(statement);
            return statement;
        }
    }
}
