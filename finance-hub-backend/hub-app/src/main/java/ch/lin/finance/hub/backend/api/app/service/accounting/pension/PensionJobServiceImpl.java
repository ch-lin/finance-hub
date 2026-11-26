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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.pension.PensionRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.parser.NenkinPdfParser;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@Service
public class PensionJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PensionJobServiceImpl.class);

    private final String importPath;
    private final PensionRecordRepository pensionRecordRepository;
    private final NenkinPdfParser nenkinPdfParser;
    private final TransactionTemplate transactionTemplate;

    public PensionJobServiceImpl(
            PensionRecordRepository pensionRecordRepository,
            NenkinPdfParser nenkinPdfParser,
            TransactionTemplate transactionTemplate,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.pensionRecordRepository = pensionRecordRepository;
        this.nenkinPdfParser = nenkinPdfParser;
        this.transactionTemplate = transactionTemplate;
        this.importPath = basePath + "/pension";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.PENSION;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Starting Pension processing. Dry run: {}, Input path: {}/source", dryRun, importPath);

        List<File> files = findPdfFiles(importPath + "/source");

        if (files.isEmpty()) {
            logger.warn("No PDF files found in the source directory: {}/source", importPath);
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try {
                PensionRecord record = nenkinPdfParser.parse(file);

                if (!dryRun) {
                    transactionTemplate.execute(status -> {
                        logger.info("Merging Pension record for date: {}", record.getRecordDate());
                        pensionRecordRepository.findByRecordDate(record.getRecordDate())
                                .ifPresentOrElse(existing -> {
                                    existing.merge(record);
                                    pensionRecordRepository.save(existing);
                                }, () -> pensionRecordRepository.save(record));
                        return null;
                    });
                } else {
                    logger.info("DRY RUN - Parsed Pension record for date: {}", record.getRecordDate());
                }

                String year = String.valueOf(record.getRecordDate().getYear());

                if (!dryRun) {
                    moveFile(file, importPath, "archive/success", year);
                }
                successFiles.add(file.getName());

            } catch (IOException | RuntimeException e) {
                logger.error("Failed to process pension file {}: {}", file.getName(), e.getMessage());
                String year = String.valueOf(LocalDate.now().getYear());
                if (!dryRun) {
                    moveFile(file, importPath, "archive/failed", year);
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }

        return new JobResult(files.size(), successFiles.size(), 0, failedFiles.size(), failedFiles);
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);

        PensionRecord record = nenkinPdfParser.parse(tempFile);

        if (!dryRun) {
            transactionTemplate.execute(status -> {
                logger.info("Merging Pension record for date: {}", record.getRecordDate());
                pensionRecordRepository.findByRecordDate(record.getRecordDate())
                        .ifPresentOrElse(existing -> {
                            existing.merge(record);
                            pensionRecordRepository.save(existing);
                        }, () -> pensionRecordRepository.save(record));
                return null;
            });
        } else {
            logger.info("DRY RUN - Parsed Pension record for date: {}", record.getRecordDate());
        }

        return new FileProcessingResult("success", dryRun, List.of(record));
    }

    private List<File> findPdfFiles(String pathStr) {
        File inputDirectory = new File(pathStr);
        if (!inputDirectory.exists() || !inputDirectory.isDirectory()) {
            return new ArrayList<>();
        }

        List<File> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(inputDirectory.toPath(), 5)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .forEach(p -> files.add(p.toFile()));
        } catch (IOException e) {
            logger.error("Failed to scan directory: {}", pathStr, e);
        }
        return files;
    }

    private void moveFile(File file, String basePath, String subDir, String year) {
        try {
            Path targetDirPath = Path.of(basePath, subDir, year);
            if (!Files.exists(targetDirPath)) {
                Files.createDirectories(targetDirPath);
            }
            Path targetFilePath = targetDirPath.resolve(file.getName());
            Files.move(file.toPath(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to move file {} to {}", file.getName(), subDir + "/" + year, e);
        }
    }
}
