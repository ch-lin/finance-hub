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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage;

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

import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.parser.MortgageParser;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.parser.MortgageParserFactory;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.result.MortgageRecordResult;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import lombok.RequiredArgsConstructor;

@Service("MortgageJobService")
@RequiredArgsConstructor
public class MortgageJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MortgageJobServiceImpl.class);

    private final MortgageParserFactory parserFactory;
    private final MortgageRecordRepository repository;

    @Value("${finance.hub.input.base-path:/app/data}")
    private String basePath;

    @Override
    public TransactionSource getSource() {
        return TransactionSource.MORTGAGE;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Executing MortgageJobServiceImpl. execute(dryRun={})", dryRun);
        String importPath = basePath + "/mortgage";

        List<File> files = HtmlArchiveUtils.findHtmlFiles(importPath + "/source");

        if (files.isEmpty()) {
            logger.warn("No HTML files found in the source directory: {}/source", importPath);
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try {
                processAndSaveFile(file, dryRun);
                String year = HtmlArchiveUtils.extractYear(null, file);
                if (!dryRun) {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/success", year);
                }
                successFiles.add(file.getName());
            } catch (Exception e) {
                logger.error("Failed to process mortgage file {}: {}", file.getName(), e.getMessage(), e);
                String year = HtmlArchiveUtils.extractYear(null, file);
                if (!dryRun) {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/failed", year);
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }

        return new JobResult(files.size(), successFiles.size(), 0, failedFiles.size(), failedFiles);
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single mortgage file: {}, dryRun: {}", tempFile.getName(), dryRun);
        MortgageRecord savedRecord = processAndSaveFile(tempFile, dryRun);
        return new FileProcessingResult("success", dryRun, MortgageRecordResult.fromEntity(savedRecord));
    }

    private MortgageRecord processAndSaveFile(File file, boolean dryRun) throws Exception {
        MortgageParser parser = parserFactory.getParser(file);
        logger.info("Using parser {} for file {}", parser.getClass().getSimpleName(), file.getName());

        MortgageRecord parsed = parser.parse(file);

        if (!dryRun) {
            Optional<MortgageRecord> existingOpt = Optional.empty();
            if (parsed.getBankName() != null && parsed.getContractNumber() != null) {
                existingOpt = repository.findByBankNameAndContractNumber(
                        parsed.getBankName(), parsed.getContractNumber());
            }

            if (existingOpt.isPresent()) {
                MortgageRecord existing = existingOpt.get();
                logger.info("Merging existing mortgage: bankName={}, contractNumber={}, loanName={}",
                        parsed.getBankName(), parsed.getContractNumber(), parsed.getLoanName());
                existing.merge(parsed);
                repository.save(existing);
                return existing;
            } else {
                logger.info("Creating new mortgage: {}", parsed.getLoanName());
                repository.save(parsed);
                return parsed;
            }
        } else {
            logger.info("DRY RUN - Parsed mortgage: {}", parsed.getLoanName());
            return parsed;
        }
    }
}
