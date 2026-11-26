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
package ch.lin.finance.hub.backend.api.app.service.currency;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.currency.ExchangeRateRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.currency.parser.ExchangeRateParseResult;
import ch.lin.finance.hub.backend.api.app.service.currency.parser.ExchangeRateParser;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;

/**
 * Job executor and file processing strategy for foreign exchange rate
 * statements.
 */
@Service
public class ExchangeRateJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateJobServiceImpl.class);

    private final ExchangeRateRecordRepository repository;
    private final List<ExchangeRateParser> parsers;
    private final TransactionTemplate transactionTemplate;
    private final String importPath;

    public ExchangeRateJobServiceImpl(
            ExchangeRateRecordRepository repository,
            List<ExchangeRateParser> parsers,
            TransactionTemplate transactionTemplate,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.repository = repository;
        this.parsers = parsers;
        this.transactionTemplate = transactionTemplate;
        this.importPath = basePath + "/exchange_rates";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.EXCHANGE_RATE;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Executing ExchangeRateJobServiceImpl. execute(dryRun={}) on importPath={}", dryRun, importPath);

        File sourceDirectory = new File(importPath, "source");
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
            logger.info("Source directory does not exist. Creating it: {}", sourceDirectory.getAbsolutePath());
            sourceDirectory.mkdirs();
            return new JobResult(0, 0, 0, 0, new HashMap<>());
        }

        List<File> files = findFiles(sourceDirectory);
        logger.info("Found {} exchange rate files in {}", files.size(), sourceDirectory.getAbsolutePath());

        if (files.isEmpty()) {
            return new JobResult(0, 0, 0, 0, new HashMap<>());
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try {
                ExchangeRateParseResult result = parseFile(file);
                if (result != null && !result.getRecords().isEmpty()) {
                    if (!dryRun) {
                        transactionTemplate.execute(status -> {
                            saveExchangeRates(result.getRecords());
                            return null;
                        });
                    }

                    String year = extractYearFromRecords(result.getRecords(), file);
                    if (!dryRun) {
                        HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/success", year);
                    }
                    successFiles.add(file.getName());
                } else {
                    throw new IllegalArgumentException("No exchange rate records parsed or no suitable parser found.");
                }
            } catch (Exception e) {
                logger.error("Failed to process exchange rate file {}: {}", file.getName(), e.getMessage(), e);
                String year = String.valueOf(LocalDate.now().getYear());
                if (!dryRun) {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/failed", year);
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }

        return new JobResult(files.size(), successFiles.size(), failedFiles.size(), 0, failedFiles);
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        return processFile(TransactionSource.EXCHANGE_RATE, tempFile, dryRun);
    }

    public FileProcessingResult processFile(TransactionSource source, File file, boolean dryRun) {
        if (source != TransactionSource.EXCHANGE_RATE) {
            return new FileProcessingResult("error", dryRun, "Unsupported transaction source: " + source);
        }

        try {
            ExchangeRateParseResult result = parseFile(file);
            if (result == null || result.getRecords().isEmpty()) {
                return new FileProcessingResult("error", dryRun, "No exchange rate records found.");
            }

            if (!dryRun) {
                transactionTemplate.execute(status -> {
                    saveExchangeRates(result.getRecords());
                    return null;
                });
            }

            return new FileProcessingResult("success", dryRun, result);
        } catch (Exception e) {
            logger.error("Error processing single exchange rate file: {}", file.getName(), e);
            return new FileProcessingResult("error", dryRun, e.getMessage());
        }
    }

    private ExchangeRateParseResult parseFile(File file) throws Exception {
        for (ExchangeRateParser parser : parsers) {
            if (parser.canParse(file)) {
                return parser.parse(file);
            }
        }
        return null;
    }

    private void saveExchangeRates(List<ExchangeRateRecord> records) {
        for (ExchangeRateRecord record : records) {
            Optional<ExchangeRateRecord> existing = repository
                    .findByRecordDateAndBaseCurrencyAndTargetCurrencyAndRateType(
                            record.getRecordDate(), record.getBaseCurrency(), record.getTargetCurrency(),
                            record.getRateType());

            if (existing.isPresent()) {
                ExchangeRateRecord target = existing.get();
                target.setRate(record.getRate());
                target.setSource(record.getSource());
                repository.save(target);
            } else {
                repository.save(record);
            }
        }
    }

    private List<File> findFiles(File sourceDirectory) throws IOException {
        List<File> fileList = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceDirectory.toPath(), 2)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv");
                    })
                    .forEach(p -> fileList.add(p.toFile()));
        }
        return fileList;
    }

    private String extractYearFromRecords(List<ExchangeRateRecord> records, File file) {
        if (records != null && !records.isEmpty() && records.get(0).getRecordDate() != null) {
            return String.valueOf(records.get(0).getRecordDate().getYear());
        }
        return HtmlArchiveUtils.extractYear(null, file);
    }
}
