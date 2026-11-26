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
package ch.lin.finance.hub.backend.api.app.service.accounting.insurance;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.result.InsurancePolicyResult;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@Service
public class InsuranceJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(InsuranceJobServiceImpl.class);

    private final String importPath;
    private final InsuranceParserFactory parserFactory;
    private final InsurancePolicyRepository insurancePolicyRepository;

    public InsuranceJobServiceImpl(
            InsuranceParserFactory parserFactory,
            InsurancePolicyRepository insurancePolicyRepository,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.parserFactory = parserFactory;
        this.insurancePolicyRepository = insurancePolicyRepository;
        this.importPath = basePath + "/insurance";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.INSURANCE;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Executing InsuranceJobServiceImpl. execute(dryRun={})", dryRun);

        List<File> files = HtmlArchiveUtils.findHtmlFiles(importPath + "/source");

        if (files.isEmpty()) {
            logger.warn("No HTML files found in the source directory: {}/source", importPath);
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try {
                ProcessResult result = processAndSaveFile(file, dryRun);
                String providerId = result.providerId();
                String year = HtmlArchiveUtils.extractYear(null, file);
                if (!dryRun) {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/success/" + providerId, year);
                }
                successFiles.add(file.getName());
            } catch (Exception e) {
                logger.error("Failed to process insurance file {}: {}", file.getName(), e.getMessage(), e);
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
        ProcessResult result = processAndSaveFile(tempFile, dryRun);
        List<InsurancePolicyResult> dtos = result.policies().stream()
                .map(p -> InsurancePolicyResult.fromEntity(p, ownerUserId))
                .toList();
        return new FileProcessingResult("success", dryRun, dtos);
    }

    private record ProcessResult(String providerId, List<InsurancePolicy> policies) {
    }

    private ProcessResult processAndSaveFile(File file, boolean dryRun) throws Exception {
        InsuranceFilePreview preview = new InsuranceFilePreview(file);
        InsuranceParser parser = parserFactory.getParser(preview);
        logger.info("Using parser {} for file {}", parser.getClass().getSimpleName(), file.getName());

        List<InsurancePolicy> newPolicies = parser.parse(preview);

        if (newPolicies.isEmpty()) {
            logger.info("No policies found in file {}", file.getName());
            return new ProcessResult(parser.getProviderId(), Collections.emptyList());
        }

        List<InsurancePolicy> savedPolicies = new ArrayList<>();
        if (!dryRun) {
            for (InsurancePolicy newPolicy : newPolicies) {
                savedPolicies.add(saveOrMergePolicy(newPolicy, parser));
            }
        } else {
            savedPolicies = newPolicies;
            logger.info("DRY RUN - Parsed {} policies from {}", newPolicies.size(), file.getName());
        }

        return new ProcessResult(parser.getProviderId(), savedPolicies);
    }

    private InsurancePolicy saveOrMergePolicy(InsurancePolicy newPolicy, InsuranceParser parser) {
        Optional<InsurancePolicy> existingOpt = insurancePolicyRepository
                .findByCompanyNameAndPolicyNumber(newPolicy.getCompanyName(), newPolicy.getPolicyNumber());

        if (existingOpt.isPresent()) {
            InsurancePolicy existing = existingOpt.get();
            parser.mergePolicy(existing, newPolicy);
            logger.info("Merged existing policy {} (No: {}, Company: {})",
                    existing.getPolicyName(), existing.getPolicyNumber(), existing.getCompanyName());
            return insurancePolicyRepository.save(existing);
        } else {
            logger.info("Added new policy {} (No: {}, Company: {})",
                    newPolicy.getPolicyName(), newPolicy.getPolicyNumber(), newPolicy.getCompanyName());
            return insurancePolicyRepository.save(newPolicy);
        }
    }
}
