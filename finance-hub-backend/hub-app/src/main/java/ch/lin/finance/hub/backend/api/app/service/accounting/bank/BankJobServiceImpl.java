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
package ch.lin.finance.hub.backend.api.app.service.accounting.bank;

import java.io.File;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Main orchestration service for Bank-related data imports.
 * <p>
 * This class handles the scanning of bank HTML files from a directory,
 * determining the appropriate parser via {@link BankParserFactory},
 * parsing the files, grouping transactions into statements, and
 * delegating persistence to {@link BankTransactionService}.
 */
@Service
public class BankJobServiceImpl implements JobExecutor, FileProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BankJobServiceImpl.class);

    private final String importPath;
    private final BankParserFactory bankParserFactory;
    private final BankTransactionService bankTransactionService;

    public BankJobServiceImpl(
            BankParserFactory bankParserFactory,
            BankTransactionService bankTransactionService,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.bankParserFactory = bankParserFactory;
        this.bankTransactionService = bankTransactionService;
        this.importPath = basePath + "/bank";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.BANK;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Executing BankJobServiceImpl. execute(dryRun={})", dryRun);
        File sourceDirectory = new File(importPath, "source");
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
            logger.info("Source directory does not exist. Creating it: {}", sourceDirectory.getAbsolutePath());
            sourceDirectory.mkdirs();
            return new JobResult(0, 0, 0, 0, new HashMap<>());
        }

        List<File> files = HtmlArchiveUtils.findHtmlFiles(sourceDirectory.getAbsolutePath());
        logger.info("Found {} HTML files in {}", files.size(), sourceDirectory.getAbsolutePath());

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try {
                ProcessResult result = processAndSaveFile(file, dryRun);
                String bankName = result.bankName();
                String safeBankName = (bankName != null && !bankName.isEmpty())
                        ? bankName.toLowerCase().replaceAll("[^a-z0-9]", "_")
                        : "unknown";

                String year = HtmlArchiveUtils.extractYear(null, file);
                if (!dryRun) {
                    HtmlArchiveUtils.moveFileAndResources(
                            file, importPath,
                            "archive/success/" + safeBankName, year);
                }
                successFiles.add(file.getName());
            } catch (Exception e) {
                logger.error("Failed to process bank file {}: {}", file.getName(), e.getMessage(), e);
                String year = HtmlArchiveUtils.extractYear(null, file);
                if (!dryRun) {
                    // Try to guess the bank name or just use "failed"
                    HtmlArchiveUtils.moveFileAndResources(
                            file, importPath,
                            "archive/failed", year);
                }
                failedFiles.put(file.getName(), e.getMessage());
            }
        }

        if (!dryRun) {
            HtmlArchiveUtils.cleanupEmptyDirectories(sourceDirectory);
        }

        return new JobResult(files.size(), successFiles.size(), 0, failedFiles.size(), failedFiles);
    }

    @Override
    public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
        logger.info("Processing single bank file: {}, dryRun: {}", tempFile.getName(), dryRun);
        ProcessResult result = processAndSaveFile(tempFile, dryRun);
        return new FileProcessingResult("success", dryRun, result.transactions());
    }

    private record ProcessResult(String bankName, List<BankTransactionResult> transactions) {}

    private ProcessResult processAndSaveFile(File file, boolean dryRun) throws Exception {
        BankFilePreview preview = new BankFilePreview(file);
        BankParser parser = bankParserFactory.getParser(preview);
        logger.info("Using parser {} for file {}", parser.getClass().getSimpleName(), file.getName());

        BankParseResult result = parser.parse(preview);
        List<BankTransaction> transactions = result.getTransactions();

        if (transactions.isEmpty()) {
            logger.info("No transactions found in file {}", file.getName());
            return new ProcessResult("unknown", Collections.emptyList());
        }

        String primaryBankName = transactions.get(0).getBankName();

        // Group transactions by bankName and YYYY-MM
        Map<String, List<BankTransaction>> groupedTransactions = transactions.stream()
                .collect(Collectors.groupingBy(tx -> {
                    String monthStr = YearMonth.from(tx.getTransactionDate())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    return tx.getBankName() + "|" + monthStr;
                }));

        List<BankTransactionResult> allTransactionResults = new ArrayList<>();

        for (Map.Entry<String, List<BankTransaction>> entry : groupedTransactions.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String bankName = parts[0];
            String statementMonth = parts[1];

            SaveBankStatementCommand.SaveBankStatementCommandBuilder commandBuilder = SaveBankStatementCommand.builder()
                    .bankName(bankName)
                    .statementMonth(statementMonth);

            List<SaveBankTransactionCommand> txCommands = new ArrayList<>();

            for (BankTransaction tx : entry.getValue()) {
                txCommands.add(SaveBankTransactionCommand.builder()
                        .transactionDate(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
                        .description(tx.getDescription())
                        .depositAmount(tx.getDepositAmount())
                        .withdrawalAmount(tx.getWithdrawalAmount())
                        .balance(tx.getBalance())
                        .memo(tx.getMemo())
                        .build());
            }
            commandBuilder.transactions(txCommands);

            if (!dryRun) {
                BankStatementResult statementResult = bankTransactionService.saveStatement(commandBuilder.build());
                if (statementResult != null && statementResult.getTransactions() != null) {
                    allTransactionResults.addAll(statementResult.getTransactions());
                } else {
                    for (BankTransaction tx : entry.getValue()) {
                        allTransactionResults.add(BankTransactionResult.fromEntity(tx));
                    }
                }
                logger.info("Saved statement for {} - {} with {} transactions", bankName, statementMonth,
                        txCommands.size());
            } else {
                logger.info("DRY RUN - Parsed statement for {} - {} with {} transactions", bankName, statementMonth,
                        txCommands.size());
                for (BankTransaction tx : entry.getValue()) {
                    allTransactionResults.add(BankTransactionResult.fromEntity(tx));
                }
            }
        }

        return new ProcessResult(primaryBankName, allTransactionResults);
    }
}
