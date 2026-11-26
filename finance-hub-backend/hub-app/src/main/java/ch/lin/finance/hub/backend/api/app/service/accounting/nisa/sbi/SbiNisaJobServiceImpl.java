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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.sbi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaContributionSettingRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Service implementation for processing SBI NISA data imports.
 * <p>
 * This job acts as a {@link JobExecutor} for scheduled automation and a
 * {@link FileProcessingStrategy} for manual, single-file imports.
 */
@Service
public class SbiNisaJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(SbiNisaJobServiceImpl.class);

    /**
     * Base path where NISA files are stored for processing.
     */
    private final String importPath;

    /**
     * Repository for managing NISA snapshot records.
     */
    private final NisaSnapshotRecordRepository nisaRepository;

    /**
     * Repository for managing NISA contribution settings.
     */
    private final NisaContributionSettingRepository nisaSettingRepository;

    /**
     * Parser specifically for SBI NISA HTML formats.
     */
    private final SbiNisaParser sbiNisaParser;

    /**
     * Template for managing transactions programmatically.
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * Template for managing transactions programmatically.
     */
    public SbiNisaJobServiceImpl(
            NisaSnapshotRecordRepository nisaRepository,
            NisaContributionSettingRepository nisaSettingRepository,
            SbiNisaParser sbiNisaParser,
            TransactionTemplate transactionTemplate,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.nisaRepository = nisaRepository;
        this.nisaSettingRepository = nisaSettingRepository;
        this.sbiNisaParser = sbiNisaParser;
        this.transactionTemplate = transactionTemplate;
        this.importPath = basePath + "/nisa";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.NISA;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Starting NISA processing. Dry run: {}, Input path: {}/source", dryRun, importPath);

        List<File> files = HtmlArchiveUtils.findHtmlFiles(importPath + "/source");

        if (files.isEmpty()) {
            logger.warn("No HTML files found in the source directory: {}/source", importPath);
            return new JobResult(0, 0, 0, 0, null);
        }

        LocalDate commonRecordDate = determineCommonRecordDate(files);

        List<NisaSnapshotRecord> allRecords = new ArrayList<>();
        List<NisaContributionSetting> allSettings = new ArrayList<>();
        List<File> successFilesToMove = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        parseFiles(files, commonRecordDate, dryRun, allRecords, allSettings, successFilesToMove, failedFiles);

        boolean dbSuccess = executeDatabaseTransaction(commonRecordDate, allRecords, allSettings, dryRun);

        List<String> successFileNames = new ArrayList<>();
        handleFileMoving(dbSuccess, dryRun, commonRecordDate, successFilesToMove, failedFiles, successFileNames);

        return new JobResult(files.size(), successFileNames.size(), 0, failedFiles.size(), failedFiles);
    }

    private LocalDate determineCommonRecordDate(List<File> files) {
        LocalDate commonRecordDate = null;
        for (File file : files) {
            LocalDate d = extractRecordDate(file);
            if (commonRecordDate == null || d.isAfter(commonRecordDate)) {
                commonRecordDate = d;
            }
        }
        return commonRecordDate;
    }

    private void parseFiles(List<File> files, LocalDate commonRecordDate, boolean dryRun,
            List<NisaSnapshotRecord> allRecords, List<NisaContributionSetting> allSettings,
            List<File> successFilesToMove, Map<String, String> failedFiles) {
        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file);
                    FileInputStream fisSettings = new FileInputStream(file)) {

                List<NisaSnapshotRecord> parsedRecords = sbiNisaParser.parse(fis, commonRecordDate);
                List<NisaContributionSetting> parsedSettings = sbiNisaParser.parseSettings(fisSettings,
                        commonRecordDate);

                allRecords.addAll(parsedRecords);
                allSettings.addAll(parsedSettings);

                successFilesToMove.add(file);

            } catch (Exception e) {
                logger.error("Hard fail parsing file {}: {}", file.getName(), e.getMessage());
                String year = String.valueOf(LocalDate.now().getYear());
                if (!dryRun) {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/failed", year);
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }
    }

    private boolean executeDatabaseTransaction(LocalDate commonRecordDate, List<NisaSnapshotRecord> allRecords,
            List<NisaContributionSetting> allSettings, boolean dryRun) {
        if (commonRecordDate == null || (allRecords.isEmpty() && allSettings.isEmpty())) {
            return true;
        }

        if (dryRun) {
            logger.info("DRY RUN - Parsed {} NISA snapshot records and {} settings for date: {}",
                    allRecords.size(), allSettings.size(), commonRecordDate);
            return true;
        }

        try {
            transactionTemplate.execute(status -> {
                saveOrMergeRecords(allRecords, allSettings);
                return null;
            });
            return true;
        } catch (DataAccessException | TransactionException e) {
            logger.error("Database transaction failed for NISA batch. Aborting move to success.", e);
            return false;
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error during NISA batch database transaction.", e);
            return false;
        }
    }

    private void handleFileMoving(boolean dbSuccess, boolean dryRun, LocalDate commonRecordDate,
            List<File> successFilesToMove, Map<String, String> failedFiles,
            List<String> successFileNames) {
        if (dryRun) {
            for (File file : successFilesToMove) {
                if (dbSuccess) {
                    successFileNames.add(file.getName());
                } else {
                    failedFiles.put(file.getName(),
                            "Database transaction failed (Key conflict or constraint violation)");
                }
            }
            return;
        }

        String year = commonRecordDate != null ? String.valueOf(commonRecordDate.getYear())
                : String.valueOf(LocalDate.now().getYear());

        if (dbSuccess) {
            for (File file : successFilesToMove) {
                try {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/success", year);
                    successFileNames.add(file.getName());
                } catch (Exception e) {
                    logger.error("Failed to move file {} to success: {}", file.getName(), e.getMessage());
                }
            }
        } else {
            for (File file : successFilesToMove) {
                try {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/db-error", year);
                    failedFiles.put(file.getName(),
                            "Database transaction failed (Key conflict or constraint violation)");
                } catch (Exception e) {
                    logger.error("Failed to move file {} to db-error: {}", file.getName(), e.getMessage());
                }
            }
        }
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);

        try (FileInputStream fis = new FileInputStream(tempFile);
                FileInputStream fisSettings = new FileInputStream(tempFile)) {
            LocalDate recordDate = extractRecordDate(tempFile);
            List<NisaSnapshotRecord> parsedRecords = sbiNisaParser.parse(fis, recordDate);
            List<NisaContributionSetting> parsedSettings = sbiNisaParser.parseSettings(fisSettings, recordDate);

            if (!dryRun) {
                transactionTemplate.execute(status -> {
                    saveOrMergeRecords(parsedRecords, parsedSettings);
                    return null;
                });
            } else {
                logger.info("DRY RUN - Parsed {} NISA snapshot records and {} settings for date: {}",
                        parsedRecords.size(), parsedSettings.size(), recordDate);
            }

            return new FileProcessingResult("success", dryRun, parsedRecords);
        }
    }

    private void saveOrMergeRecords(List<NisaSnapshotRecord> records, List<NisaContributionSetting> settings) {
        if (records != null && !records.isEmpty()) {
            for (NisaSnapshotRecord incoming : records) {
                Optional<NisaSnapshotRecord> existingOpt = nisaRepository
                        .findByRecordDateAndAccountTypeAndFundCode(
                                incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundCode());
                if (existingOpt.isPresent()) {
                    NisaSnapshotRecord existing = existingOpt.get();
                    existing.merge(incoming);
                    logger.info("Updating existing NISA snapshot record for {} - {} [{}]", existing.getAccountType(),
                            existing.getFundName(), existing.getFundCode());
                    nisaRepository.save(existing);
                } else {
                    inheritHistoricalRate(incoming);
                    logger.info("Saving new NISA snapshot record for {} - {} [{}]", incoming.getAccountType(),
                            incoming.getFundName(), incoming.getFundCode());
                    nisaRepository.save(incoming);
                }
            }
        }

        if (settings != null && !settings.isEmpty()) {
            for (NisaContributionSetting incoming : settings) {
                Optional<NisaContributionSetting> existingOpt = nisaSettingRepository
                        .findByRecordDateAndAccountTypeAndFundCodeAndCourse(
                                incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundCode(),
                                incoming.getCourse());
                if (existingOpt.isPresent()) {
                    NisaContributionSetting existing = existingOpt.get();
                    existing.merge(incoming);
                    logger.info("Updating existing NISA setting for {} - {} [{}] ({})", existing.getAccountType(),
                            existing.getFundName(), existing.getFundCode(), existing.getCourse());
                    nisaSettingRepository.save(existing);
                } else {
                    inheritHistoricalRate(incoming);
                    logger.info("Saving new NISA setting for {} - {} [{}] ({})", incoming.getAccountType(),
                            incoming.getFundName(), incoming.getFundCode(), incoming.getCourse());
                    nisaSettingRepository.save(incoming);
                }
            }
        }
    }

    private void inheritHistoricalRate(NisaSnapshotRecord record) {
        Double rate = nisaRepository
                .findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(
                        record.getAccountType(), record.getFundCode())
                .map(r -> r.getExpectedReturnRate())
                .orElse(null);
        record.updateExpectedReturnRate(rate);
    }

    private void inheritHistoricalRate(NisaContributionSetting setting) {
        Double rate = nisaSettingRepository
                .findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(
                        setting.getAccountType(), setting.getFundCode())
                .map(s -> s.getExpectedReturnRate())
                .orElse(null);
        setting.updateExpectedReturnRate(rate);
    }

    private LocalDate extractRecordDate(File file) {
        try {
            return Files.getLastModifiedTime(file.toPath())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (IOException | SecurityException e) {
            logger.warn("Failed to get last modified time for file {}, defaulting to today", file.getName());
            return LocalDate.now();
        }
    }
}
