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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc;

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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionFundSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionPageType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@Service
public class DefinedContributionJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DefinedContributionJobServiceImpl.class);

    private final String basePath;
    private final DefinedContributionAccountSnapshotRepository dcRepository;
    private final DefinedContributionFundSnapshotRepository dcFundRepository;
    private final List<DefinedContributionParser> parsers;
    private final TransactionTemplate transactionTemplate;
    private final List<String> targetFolders = List.of("dc", "ideco");

    public DefinedContributionJobServiceImpl(
            DefinedContributionAccountSnapshotRepository dcRepository,
            DefinedContributionFundSnapshotRepository dcFundRepository,
            List<DefinedContributionParser> parsers,
            TransactionTemplate transactionTemplate,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.dcRepository = dcRepository;
        this.dcFundRepository = dcFundRepository;
        this.parsers = parsers;
        this.transactionTemplate = transactionTemplate;
        this.basePath = basePath;
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.DEFINED_CONTRIBUTION;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Starting DC/iDeCo processing. Dry run: {}, Input base path: {}", dryRun, basePath);

        List<File> allFiles = new ArrayList<>();
        Map<File, String> fileToFolderMap = new HashMap<>();

        for (String folder : targetFolders) {
            String sourcePath = basePath + "/" + folder + "/source";
            List<File> files = HtmlArchiveUtils.findHtmlFiles(sourcePath);
            allFiles.addAll(files);
            for (File f : files) {
                fileToFolderMap.put(f, folder);
            }
        }

        if (allFiles.isEmpty()) {
            logger.warn("No HTML files found in the source directories under {}", basePath);
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : allFiles) {
            String folder = fileToFolderMap.get(file);
            String importPath = basePath + "/" + folder;

            try {
                ParsedDcSnapshot parsed = parseFileWithAllParsers(file);

                if (parsed != null) {
                    DefinedContributionAccountSnapshot snapshot = parsed.snapshot();
                    applyExpectedReturnRates(snapshot, null);

                    if (!dryRun) {
                        transactionTemplate.execute(status -> {
                            logger.info("Saving {} snapshot record for date: {}", folder, snapshot.getRecordDate());
                            DefinedContributionAccountSnapshot existing = dcRepository
                                    .findByRecordDateAndType(snapshot.getRecordDate(), snapshot.getType()).orElse(null);
                            if (existing != null) {
                                mergeSnapshotFacts(existing, snapshot, parsed.pageType());
                                return dcRepository.save(existing);
                            } else {
                                return dcRepository.save(snapshot);
                            }
                        });
                    } else {
                        logger.info("DRY RUN - Parsed {} snapshot record for date: {}", folder,
                                snapshot.getRecordDate());
                    }

                    String year = String.valueOf(snapshot.getRecordDate().getYear());
                    if (!dryRun) {
                        HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/success", year);
                    }
                    successFiles.add(file.getName());
                } else {
                    throw new IllegalArgumentException("No suitable parser found for the file.");
                }

            } catch (IOException | RuntimeException e) {
                logger.error("Hard fail parsing file {} from folder {}: {}", file.getName(), folder, e.getMessage());
                String year = String.valueOf(LocalDate.now().getYear());
                if (!dryRun) {
                    HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/failed", year);
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }

        return new JobResult(allFiles.size(), successFiles.size(), 0, failedFiles.size(), failedFiles);
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);

        ParsedDcSnapshot parsed = parseFileWithAllParsers(tempFile);
        if (parsed == null) {
            throw new IllegalArgumentException("No suitable parser found for the file.");
        }
        DefinedContributionAccountSnapshot snapshot = parsed.snapshot();

        applyExpectedReturnRates(snapshot, ownerUserId);

        if (!dryRun) {
            boolean hasOwner = ownerUserId != null && !ownerUserId.isBlank();
            transactionTemplate.execute(status -> {
                logger.info("Saving DC snapshot record for date: {}", snapshot.getRecordDate());
                DefinedContributionAccountSnapshot existing = (hasOwner
                        ? dcRepository.findByOwnerUserIdAndRecordDateAndType(ownerUserId, snapshot.getRecordDate(),
                                snapshot.getType())
                        : dcRepository.findByRecordDateAndType(snapshot.getRecordDate(), snapshot.getType()))
                        .orElse(null);
                if (existing != null) {
                    mergeSnapshotFacts(existing, snapshot, parsed.pageType());
                    return dcRepository.save(existing);
                } else {
                    return dcRepository.save(snapshot);
                }
            });
        } else {
            logger.info("DRY RUN - Parsed DC snapshot record for date: {}", snapshot.getRecordDate());
        }

        return new FileProcessingResult("success", dryRun,
                List.of(DefinedContributionAccountSnapshotResult.fromEntity(snapshot)));
    }

    public record ParsedDcSnapshot(
            DefinedContributionAccountSnapshot snapshot,
            DefinedContributionPageType pageType) {
    }

    private void mergeSnapshotFacts(DefinedContributionAccountSnapshot existing,
            DefinedContributionAccountSnapshot incoming,
            DefinedContributionPageType pageType) {
        switch (pageType) {
            case MEMBER_INFO -> existing.mergeMemberInfoFacts(incoming);
            case ALLOCATION -> existing.mergeAllocationFacts(incoming);
            case STATUS -> existing.mergeStatusFacts(incoming);
        }
    }

    private ParsedDcSnapshot parseFileWithAllParsers(File file) throws IOException {
        Document doc;
        try (FileInputStream fis = new FileInputStream(file)) {
            doc = Jsoup.parse(fis, "Shift_JIS", "");
        }

        LocalDate fallbackDate;
        try {
            fallbackDate = Files.getLastModifiedTime(file.toPath())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (IOException | SecurityException e) {
            logger.warn("Failed to get last modified time for file {}, defaulting to today", file.getName());
            fallbackDate = LocalDate.now();
        }

        for (DefinedContributionParser parser : parsers) {
            try {
                if (parser.canParse(doc)) {
                    LocalDate recordDate = parser.extractRecordDate(doc, fallbackDate);
                    DefinedContributionAccountSnapshot snapshot = parser.parse(doc, recordDate);
                    return new ParsedDcSnapshot(snapshot, parser.getPageType());
                }
            } catch (RuntimeException e) {
                // Try the next parser if possible
                logger.debug("Parser {} failed: {}", parser.getClass().getSimpleName(), e.getMessage());
            }
        }
        return null;
    }

    private void applyExpectedReturnRates(DefinedContributionAccountSnapshot snapshot, String ownerUserId) {
        if (snapshot == null || snapshot.getFunds() == null || snapshot.getFunds().isEmpty()) {
            return;
        }

        boolean hasOwner = ownerUserId != null && !ownerUserId.isBlank();
        for (DefinedContributionFundSnapshot fund : snapshot.getFunds()) {
            // Find the most recent snapshot for this fund
            Double rate = (hasOwner
                    ? dcFundRepository
                            .findFirstByOwnerUserIdAndCommodityTypeAndFundNameOrderByAccountSnapshot_RecordDateDesc(
                                    ownerUserId, fund.getCommodityType(), fund.getFundName())
                    : dcFundRepository.findFirstByCommodityTypeAndFundNameOrderByAccountSnapshot_RecordDateDesc(
                            fund.getCommodityType(), fund.getFundName()))
                    .map(f -> f.getExpectedReturnRate()).orElse(null); // Keep null if no history found

            fund.updateExpectedReturnRate(rate);
        }
    }
}
