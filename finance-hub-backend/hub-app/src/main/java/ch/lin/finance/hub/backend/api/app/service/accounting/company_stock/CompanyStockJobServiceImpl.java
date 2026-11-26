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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeBenefitEsppPurchaseRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeBenefitRsuEventRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeBenefitRsuGrantRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeBenefitRsuVestScheduleRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeCashTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeEsppStockLotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeRsuStockLotRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command.SaveETradeExtensionCommand;
import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.ETradeParser;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeBenefitHistoryParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeHoldingsParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitEsppPurchaseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuEventResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuGrantResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuVestScheduleResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeCashTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.StockLotResult;
import ch.lin.finance.hub.backend.api.app.service.job.JobExecutor;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.service.job.file.utils.HtmlArchiveUtils;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.BaseStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitEsppPurchase;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuEvent;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuGrant;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuVestSchedule;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeCashTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeEsppStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRsuStockLot;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@Service
public class CompanyStockJobServiceImpl implements JobExecutor, FileProcessingStrategy, CompanyStockService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyStockJobServiceImpl.class);

    private final ETradeAccountSnapshotRepository repository;
    private final ETradeEsppStockLotRepository esppStockLotRepository;
    private final ETradeRsuStockLotRepository rsuStockLotRepository;
    private final ETradeCashTransactionRepository cashTransactionRepository;
    private final ETradeBenefitEsppPurchaseRepository esppRepository;
    private final ETradeBenefitRsuGrantRepository rsuGrantRepository;
    private final ETradeBenefitRsuEventRepository rsuEventRepository;
    private final ETradeBenefitRsuVestScheduleRepository rsuVestScheduleRepository;
    private final List<ETradeParser<?>> parsers;
    private final TransactionTemplate transactionTemplate;
    private final String importPath;

    public CompanyStockJobServiceImpl(ETradeAccountSnapshotRepository repository,
            ETradeEsppStockLotRepository esppStockLotRepository,
            ETradeRsuStockLotRepository rsuStockLotRepository,
            ETradeCashTransactionRepository cashTransactionRepository,
            ETradeBenefitEsppPurchaseRepository esppRepository,
            ETradeBenefitRsuGrantRepository rsuGrantRepository,
            ETradeBenefitRsuEventRepository rsuEventRepository,
            ETradeBenefitRsuVestScheduleRepository rsuVestScheduleRepository,
            List<ETradeParser<?>> parsers,
            TransactionTemplate transactionTemplate,
            @Value("${finance.hub.input.base-path:/app/data}") String basePath) {
        this.repository = repository;
        this.esppStockLotRepository = esppStockLotRepository;
        this.rsuStockLotRepository = rsuStockLotRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.esppRepository = esppRepository;
        this.rsuGrantRepository = rsuGrantRepository;
        this.rsuEventRepository = rsuEventRepository;
        this.rsuVestScheduleRepository = rsuVestScheduleRepository;
        this.parsers = parsers;
        this.transactionTemplate = transactionTemplate;
        this.importPath = basePath + "/etrade";
    }

    @Override
    public TransactionSource getSource() {
        return TransactionSource.COMPANY_STOCK;
    }

    @Override
    public JobResult execute(boolean dryRun) throws Exception {
        logger.info("Starting Company Stock processing. Dry run: {}, Input path: {}/source", dryRun, importPath);

        List<File> files = HtmlArchiveUtils.findHtmlFiles(importPath + "/source");

        if (files.isEmpty()) {
            logger.warn("No HTML files found in the source directory: {}/source", importPath);
            return new JobResult(0, 0, 0, 0, null);
        }

        List<String> successFiles = new ArrayList<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (File file : files) {
            try {
                Object result = parseFileWithAllParsers(file);

                if (result != null) {
                    if (!dryRun) {
                        transactionTemplate.execute(status -> {
                            handleParsedResult(result);
                            return null;
                        });
                    } else {
                        logger.info("DRY RUN - Parsed E-Trade file successfully.");
                    }

                    // For now, assume snapshot recordDate for archiving
                    String year = String.valueOf(LocalDate.now().getYear());
                    if (result instanceof ETradeHoldingsParseResult hParsed) {
                        if (hParsed.getSnapshot() != null) {
                            year = String.valueOf(hParsed.getSnapshot().getRecordDate().getYear());
                        }
                    }

                    if (!dryRun) {
                        HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/success", year);
                    }
                    successFiles.add(file.getName());
                } else {
                    throw new IllegalArgumentException("No suitable parser found for the file.");
                }

            } catch (IOException | RuntimeException e) {
                logger.error("Hard fail parsing file {}: {}", file.getName(), e.getMessage());
                String year = String.valueOf(LocalDate.now().getYear());
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
        logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);

        Object result = parseFileWithAllParsers(tempFile);
        if (result == null) {
            throw new IllegalArgumentException("No suitable parser found for the file.");
        }

        if (!dryRun) {
            transactionTemplate.execute(status -> {
                handleParsedResult(result);
                return null;
            });
        } else {
            logger.info("DRY RUN - Parsed E-Trade file successfully.");
        }

        return new FileProcessingResult("success", dryRun, List.of(result));
    }

    @Override
    public void saveExtensionData(SaveETradeExtensionCommand command, boolean dryRun) {
        String symbol = (command.getLots() != null && !command.getLots().isEmpty()
                && command.getLots().get(0).getSymbol() != null)
                        ? command.getLots().get(0).getSymbol()
                        : "UNKNOWN";

        ETradeAccountSnapshot snapshot = new ETradeAccountSnapshot(symbol, command.getRecordDate(),
                command.getTotalAccountValue().toDomain(), command.getCurrentAccountValue().toDomain(),
                command.getPotentialBenefitValue().toDomain(), command.getEsppPurchasedQty(), command.getRsGrantedQty(),
                command.getExchangeRate());

        List<ETradeEsppStockLot> esppLots = new ArrayList<>();
        List<ETradeRsuStockLot> rsuLots = new ArrayList<>();

        if (command.getLots() != null) {
            for (SaveETradeExtensionCommand.Lot lotReq : command.getLots()) {
                LocalDate lotRecordDate = lotReq.getRecordDate() != null ? lotReq.getRecordDate()
                        : command.getRecordDate();
                if (lotReq.getRecordType() == ETradeRecordType.ESPP) {
                    ETradeEsppStockLot esppLot = new ETradeEsppStockLot(
                            lotReq.getSymbol(),
                            lotRecordDate,
                            lotReq.getPrice().toDomain(),
                            lotReq.getQuantity(),
                            lotReq.getSellableQuantity(),
                            lotReq.getPrice().toDomain(),
                            lotReq.getEstMarketValue().toDomain(),
                            lotReq.getExpectedGainLoss().toDomain(),
                            lotReq.getDiscountPercent());
                    esppLots.add(esppLot);
                } else {
                    String grantNumber = "EXT-" + lotRecordDate.toString().replace("-", "");
                    ETradeRsuStockLot rsuLot = new ETradeRsuStockLot(
                            lotReq.getSymbol(),
                            grantNumber,
                            lotRecordDate,
                            1,
                            lotRecordDate,
                            lotReq.getSellableQuantity(),
                            lotReq.getPrice().toDomain(),
                            lotReq.getEstMarketValue().toDomain(),
                            lotReq.getExpectedGainLoss().toDomain(),
                            null,
                            null);
                    rsuLots.add(rsuLot);
                }
            }
        }

        List<ETradeCashTransaction> cashTxs = new ArrayList<>();
        if (command.getCashTransactions() != null) {
            for (SaveETradeExtensionCommand.CashTransaction txReq : command.getCashTransactions()) {
                cashTxs.add(new ETradeCashTransaction(
                        symbol,
                        txReq.getTransactionDate(),
                        txReq.getDescription(),
                        txReq.getAmount() != null ? txReq.getAmount().toDomain() : null));
            }
        }

        if (!dryRun) {
            transactionTemplate.execute(status -> {
                repository.findBySymbolAndRecordDate(snapshot.getSymbol(), snapshot.getRecordDate())
                        .ifPresentOrElse(existing -> {
                            existing.merge(snapshot);
                            repository.save(existing);
                        }, () -> repository.save(snapshot));
                logger.info("Saved E-Trade snapshot from extension for date: {}", command.getRecordDate());

                for (ETradeEsppStockLot esppLot : esppLots) {
                    esppStockLotRepository.findBySymbolAndPurchaseDate(esppLot.getSymbol(), esppLot.getPurchaseDate())
                            .ifPresentOrElse(existing -> {
                                existing.merge(esppLot);
                                esppStockLotRepository.save(existing);
                            }, () -> esppStockLotRepository.save(esppLot));
                }

                for (ETradeRsuStockLot rsuLot : rsuLots) {
                    rsuStockLotRepository.findBySymbolAndGrantNumberAndVestPeriod(
                            rsuLot.getSymbol(), rsuLot.getGrantNumber(), rsuLot.getVestPeriod())
                            .ifPresentOrElse(existing -> {
                                existing.merge(rsuLot);
                                rsuStockLotRepository.save(existing);
                            }, () -> rsuStockLotRepository.save(rsuLot));
                }
                logger.info("Merged E-Trade stock lots from extension ({} ESPP lots, {} RSU lots)", esppLots.size(),
                        rsuLots.size());

                int insertedCashTxs = 0;
                for (ETradeCashTransaction tx : cashTxs) {
                    if (!cashTransactionRepository.existsBySymbolAndTransactionDateAndDescriptionAndAmount(
                            tx.getSymbol(), tx.getTransactionDate(), tx.getDescription(), tx.getAmount())) {
                        cashTransactionRepository.save(tx);
                        insertedCashTxs++;
                    }
                }
                logger.info("Inserted {} new cash transactions from extension", insertedCashTxs);
                return null;
            });
        } else {
            logger.info("DRY RUN - E-Trade snapshot from extension parsed successfully (lots: {}, cash txs: {})",
                    esppLots.size() + rsuLots.size(), cashTxs.size());
        }
    }

    @Override
    public List<ETradeAccountSnapshotResult> getAllSnapshots() {
        return repository.findAll().stream().map(ETradeAccountSnapshotResult::fromEntity).toList();
    }

    @Override
    public List<StockLotResult> getStockLots() {
        List<StockLotResult> results = new ArrayList<>();
        esppStockLotRepository.findAll().forEach(lot -> results.add(StockLotResult.fromEntity(lot)));
        rsuStockLotRepository.findAll().forEach(lot -> results.add(StockLotResult.fromEntity(lot)));
        return results;
    }

    @Override
    public List<ETradeCashTransactionResult> getCashTransactions() {
        return cashTransactionRepository.findAll().stream().map(ETradeCashTransactionResult::fromEntity).toList();
    }

    @Override
    public List<ETradeBenefitEsppPurchaseResult> getEsppPurchases() {
        return esppRepository.findAll().stream().map(ETradeBenefitEsppPurchaseResult::fromEntity).toList();
    }

    @Override
    public List<ETradeBenefitRsuGrantResult> getRsuGrants() {
        return rsuGrantRepository.findAll().stream().map(ETradeBenefitRsuGrantResult::fromEntity).toList();
    }

    @Override
    public List<ETradeBenefitRsuEventResult> getRsuEvents() {
        return rsuEventRepository.findAll().stream().map(ETradeBenefitRsuEventResult::fromEntity).toList();
    }

    @Override
    public List<ETradeBenefitRsuVestScheduleResult> getRsuVestSchedules() {
        return rsuVestScheduleRepository.findAll().stream().map(ETradeBenefitRsuVestScheduleResult::fromEntity)
                .toList();
    }

    private void handleParsedResult(Object result) {
        switch (result) {
            case ETradeHoldingsParseResult parsed -> {
                if (parsed.getSnapshot() != null) {
                    ETradeAccountSnapshot snapshot = parsed.getSnapshot();
                    logger.info("Saving E-Trade snapshot record for symbol: {}, date: {}", snapshot.getSymbol(),
                            snapshot.getRecordDate());
                    repository.findBySymbolAndRecordDate(snapshot.getSymbol(), snapshot.getRecordDate())
                            .ifPresentOrElse(existing -> {
                                existing.merge(snapshot);
                                repository.save(existing);
                            }, () -> repository.save(snapshot));
                }

                List<ETradeEsppStockLot> esppLots = new ArrayList<>();
                List<ETradeRsuStockLot> rsuLots = new ArrayList<>();
                for (BaseStockLot lot : parsed.getLots()) {
                    switch (lot) {
                        case ETradeEsppStockLot espp ->
                            esppLots.add(espp);
                        case ETradeRsuStockLot rsu ->
                            rsuLots.add(rsu);
                        default -> {
                        }
                    }
                }

                for (ETradeEsppStockLot esppLot : esppLots) {
                    esppStockLotRepository.findBySymbolAndPurchaseDate(esppLot.getSymbol(), esppLot.getPurchaseDate())
                            .ifPresentOrElse(existing -> {
                                existing.merge(esppLot);
                                esppStockLotRepository.save(existing);
                            }, () -> esppStockLotRepository.save(esppLot));
                }

                for (ETradeRsuStockLot rsuLot : rsuLots) {
                    rsuStockLotRepository.findBySymbolAndGrantNumberAndVestPeriod(
                            rsuLot.getSymbol(), rsuLot.getGrantNumber(), rsuLot.getVestPeriod())
                            .ifPresentOrElse(existing -> {
                                existing.merge(rsuLot);
                                rsuStockLotRepository.save(existing);
                            }, () -> rsuStockLotRepository.save(rsuLot));
                }
                logger.info("Merged E-Trade stock lots ({} ESPP lots, {} RSU lots)", esppLots.size(), rsuLots.size());

                logger.info("Processing Cash Transactions ({} parsed)", parsed.getCashTransactions().size());
                int insertedCashTxs = 0;
                for (ETradeCashTransaction tx : parsed.getCashTransactions()) {
                    if (!cashTransactionRepository.existsBySymbolAndTransactionDateAndDescriptionAndAmount(
                            tx.getSymbol(), tx.getTransactionDate(), tx.getDescription(), tx.getAmount())) {
                        cashTransactionRepository.save(tx);
                        insertedCashTxs++;
                    }
                }
                logger.info("Inserted {} new cash transactions (skipped existing)", insertedCashTxs);
            }
            case ETradeBenefitHistoryParseResult parsed -> {
                logger.info("Merging E-Trade ESPP and RSU benefit records");

                if (parsed.getEsppPurchases() != null) {
                    for (ETradeBenefitEsppPurchase purchase : parsed.getEsppPurchases()) {
                        esppRepository.findBySymbolAndPurchaseDate(purchase.getSymbol(), purchase.getPurchaseDate())
                                .ifPresentOrElse(existing -> {
                                    existing.merge(purchase);
                                    esppRepository.save(existing);
                                }, () -> esppRepository.save(purchase));
                    }
                }

                if (parsed.getRsuGrants() != null) {
                    for (ETradeBenefitRsuGrant grant : parsed.getRsuGrants()) {
                        rsuGrantRepository.findBySymbolAndGrantNumber(grant.getSymbol(), grant.getGrantNumber())
                                .ifPresentOrElse(existing -> {
                                    existing.merge(grant);
                                    rsuGrantRepository.save(existing);
                                }, () -> rsuGrantRepository.save(grant));
                    }
                }

                if (parsed.getRsuEvents() != null) {
                    for (ETradeBenefitRsuEvent event : parsed.getRsuEvents()) {
                        rsuEventRepository.findBySymbolAndGrantNumberAndEventDateAndEventTypeAndQuantity(
                                event.getSymbol(), event.getGrantNumber(), event.getEventDate(), event.getEventType(),
                                event.getQuantity())
                                .ifPresentOrElse(existing -> {
                                    existing.merge(event);
                                    rsuEventRepository.save(existing);
                                }, () -> rsuEventRepository.save(event));
                    }
                }

                if (parsed.getRsuVestSchedules() != null) {
                    for (ETradeBenefitRsuVestSchedule schedule : parsed.getRsuVestSchedules()) {
                        rsuVestScheduleRepository.findBySymbolAndGrantNumberAndVestPeriod(
                                schedule.getSymbol(), schedule.getGrantNumber(), schedule.getVestPeriod())
                                .ifPresentOrElse(existing -> {
                                    existing.merge(schedule);
                                    rsuVestScheduleRepository.save(existing);
                                }, () -> rsuVestScheduleRepository.save(schedule));
                    }
                }
            }
            default -> {
            }
        }
    }

    private Object parseFileWithAllParsers(File file) throws IOException {
        LocalDate recordDate = extractRecordDate(file);
        for (ETradeParser<?> parser : parsers) {
            if (parser.canParse(file)) {
                try {
                    return parser.parse(file, recordDate);
                } catch (HtmlParsingException e) {
                    throw e;
                } catch (IllegalArgumentException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Invalid page type")) {
                        // Try the next parser
                        continue;
                    }
                    throw e; // propagate other errors
                } catch (IOException | RuntimeException e) {
                    // Try the next parser if possible
                    logger.debug("Parser {} failed: {}", parser.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
        return null;
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
