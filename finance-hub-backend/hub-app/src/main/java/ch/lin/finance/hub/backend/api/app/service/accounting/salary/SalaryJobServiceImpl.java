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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
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

import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Service implementation for processing salary slip HTML files.
 * <p>
 * This job acts as a {@link JobExecutor} for scheduled automation and a
 * {@link FileProcessingStrategy} for manual, single-file imports.
 */
@Service
public class SalaryJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(SalaryJobServiceImpl.class);

    /**
     * Base path where salary statement files are stored for processing.
     */
    private final String importPath;

    /**
     * Repository for managing salary records.
     */
    private final SalaryRecordRepository salaryRecordRepository;

    /**
     * Parser specifically for salary slip HTML formats.
     */
    private final SalaryParser salaryParser;

    /**
     * Template for managing transactions programmatically.
     */
    private final TransactionTemplate transactionTemplate;

    public SalaryJobServiceImpl(
            SalaryRecordRepository salaryRecordRepository,
            SalaryParser salaryParser,
            TransactionTemplate transactionTemplate,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.salaryRecordRepository = salaryRecordRepository;
        this.salaryParser = salaryParser;
        this.transactionTemplate = transactionTemplate;
        this.importPath = basePath + "/payment";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.SALARY;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Starting Salary statement processing. Dry run: {}, Input path: {}/source", dryRun, importPath);

        List<File> files = ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils
                .findHtmlFiles(importPath + "/source");

        if (files.isEmpty()) {
            logger.warn("No HTML files found in the source directory: {}/source", importPath);
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                SalaryRecord parsedRecord = salaryParser.parse(fis, file.getName());
                SalaryRecord finalRecordToSave;

                if (!dryRun) {
                    finalRecordToSave = transactionTemplate.execute(status -> {
                        Optional<SalaryRecord> existingOpt = salaryRecordRepository
                                .findByPaymentDate(parsedRecord.getPaymentDate());
                        if (existingOpt.isPresent()) {
                            SalaryRecord existingRecord = existingOpt.get();
                            // In a real scenario we might have an updateFrom method, but here we can just
                            // delete & save, or map fields manually.
                            // Since it's a simple entity without complex children yet, we update manually.
                            existingRecord.setGrossAmount(parsedRecord.getGrossAmount());
                            existingRecord.setNetAmount(parsedRecord.getNetAmount());
                            existingRecord.setBaseSalary(parsedRecord.getBaseSalary());
                            existingRecord.setOvertimePay(parsedRecord.getOvertimePay());
                            existingRecord.setCommission(parsedRecord.getCommission());
                            existingRecord.setFitnessAllowance(parsedRecord.getFitnessAllowance());
                            existingRecord.setHealthInsurance(parsedRecord.getHealthInsurance());
                            existingRecord.setPension(parsedRecord.getPension());
                            existingRecord.setEmploymentInsurance(parsedRecord.getEmploymentInsurance());
                            existingRecord.setIncomeTax(parsedRecord.getIncomeTax());
                            existingRecord.setResidentTax(parsedRecord.getResidentTax());
                            existingRecord.setEsppContribution(parsedRecord.getEsppContribution());
                            existingRecord.setNote(parsedRecord.getNote());
                            existingRecord.setDetails(parsedRecord.getDetails());

                            logger.info("Updating existing Salary record: {}", parsedRecord.getPaymentDate());
                            return salaryRecordRepository.save(existingRecord);
                        } else {
                            logger.info("Saving new Salary record: {}", parsedRecord.getPaymentDate());
                            return salaryRecordRepository.save(parsedRecord);
                        }
                    });
                } else {
                    finalRecordToSave = parsedRecord;
                    logger.info("DRY RUN - Parsed Salary record: {}", parsedRecord.getPaymentDate());
                }

                String year = String.valueOf(finalRecordToSave != null ? finalRecordToSave.getPaymentDate().getYear()
                        : LocalDate.now().getYear());

                if (!dryRun) {
                    ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils.moveFileAndResources(
                            file,
                            importPath, "archive/success", year);
                }
                successFiles.add(file.getName());

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

        return new JobResult(files.size(), successFiles.size(), 0, failedFiles.size(),
                failedFiles);
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);

        try (FileInputStream fis = new FileInputStream(tempFile)) {
            SalaryRecord parsedRecord = salaryParser.parse(fis, tempFile.getName());
            SalaryRecord finalRecordToSave;

            if (!dryRun) {
                finalRecordToSave = transactionTemplate.execute(status -> {
                    Optional<SalaryRecord> existingOpt = salaryRecordRepository
                            .findByPaymentDate(parsedRecord.getPaymentDate());
                    if (existingOpt.isPresent()) {
                        SalaryRecord existingRecord = existingOpt.get();
                        existingRecord.setGrossAmount(parsedRecord.getGrossAmount());
                        existingRecord.setNetAmount(parsedRecord.getNetAmount());
                        existingRecord.setBaseSalary(parsedRecord.getBaseSalary());
                        existingRecord.setOvertimePay(parsedRecord.getOvertimePay());
                        existingRecord.setCommission(parsedRecord.getCommission());
                        existingRecord.setFitnessAllowance(parsedRecord.getFitnessAllowance());
                        existingRecord.setHealthInsurance(parsedRecord.getHealthInsurance());
                        existingRecord.setPension(parsedRecord.getPension());
                        existingRecord.setEmploymentInsurance(parsedRecord.getEmploymentInsurance());
                        existingRecord.setIncomeTax(parsedRecord.getIncomeTax());
                        existingRecord.setResidentTax(parsedRecord.getResidentTax());
                        existingRecord.setEsppContribution(parsedRecord.getEsppContribution());
                        existingRecord.setNote(parsedRecord.getNote());
                        existingRecord.setDetails(parsedRecord.getDetails());
                        logger.info("Updating existing Salary record: {}", parsedRecord.getPaymentDate());
                        return salaryRecordRepository.save(existingRecord);
                    } else {
                        logger.info("Saving new Salary record: {}", parsedRecord.getPaymentDate());
                        return salaryRecordRepository.save(parsedRecord);
                    }
                });
            } else {
                finalRecordToSave = parsedRecord;
                logger.info("DRY RUN - Parsed Salary record: {}", parsedRecord.getPaymentDate());
            }

            return new FileProcessingResult("success", dryRun, finalRecordToSave);
        }
    }
}
