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
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.CompanyStockService;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command.SaveETradeExtensionCommand;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.CompanyStockMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.request.ETradeExtensionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.response.CompanyStockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for managing company stock assets (ESPP, RSU).
 * Provides endpoints for uploading E-Trade holdings, extension payloads, and
 * retrieving stock data.
 */
@RestController
@RequestMapping("/assets/company-stock")
@RequiredArgsConstructor
@Tag(name = "Company Stock API", description = "Endpoints for managing Company Stock (ESPP, RSU) data.")
public class CompanyStockController {

    /** Logger for this controller. */
    private static final Logger logger = LoggerFactory.getLogger(CompanyStockController.class);

    /** Service for handling company stock business logic. */
    private final CompanyStockService companyStockService;

    /** Mapper for converting between DTOs and domain models. */
    private final CompanyStockMapper mapper;

    /**
     * Uploads an E-Trade holdings HTML file for processing.
     *
     * @param file   The uploaded HTML file.
     * @param dryRun Whether to perform a dry run without saving data.
     * @return A ResponseEntity containing the file processing result.
     */
    @Operation(summary = "Upload E-Trade Holdings HTML file", description = "Upload a holdings.html file to update the current company stock asset snapshot.")
    @PostMapping(value = "/etrade/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileProcessingResult> uploadETradeFile(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {

        logger.info("Received request to upload E-Trade file: {}, dryRun: {}", file.getOriginalFilename(), dryRun);
        File tempFile = null;
        try {
            tempFile = Files.createTempFile("etrade-upload-", ".html").toFile();
            file.transferTo(Objects.requireNonNull(tempFile));
            // Note: processFile is part of DataImportJobService/FileProcessingStrategy
            // which is also implemented by the service implementation.
            // Casting or changing the interface may be needed if processFile isn't in
            // CompanyStockService.
            // However, assuming the injected bean is the implementation, or we can use the
            // specific interface.
            // For now, since CompanyStockJobServiceImpl implements it, we can cast it if
            // necessary, but actually processFile is part of DataImportJobService.
            // To keep it simple, we cast it to FileProcessingStrategy if needed, or we just
            // leave it if it compiles.
            FileProcessingResult result = ((FileProcessingStrategy) companyStockService)
                    .processFile(tempFile, dryRun, SecurityUtil.getCurrentUserId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to process E-Trade file", e);
            return ResponseEntity.badRequest()
                    .body(new FileProcessingResult("error: " + e.getMessage(), dryRun, null));
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    logger.warn("Could not delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Receives and processes an E-Trade JSON payload from a Chrome Extension.
     *
     * @param request The parsed E-Trade extension request payload.
     * @param dryRun  Whether to perform a dry run without saving data.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @Operation(summary = "Upload E-Trade data from Chrome Extension", description = "Upload a parsed E-Trade JSON payload to update the snapshot.")
    @PostMapping("/etrade/extension")
    public ResponseEntity<Void> receiveETradeFromExtension(@RequestBody ETradeExtensionRequest request,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {

        logger.info("Received E-Trade data from Chrome Extension for date: {}, dryRun: {}", request.recordDate(),
                dryRun);
        try {
            SaveETradeExtensionCommand command = mapper.toCommand(request);
            companyStockService.saveExtensionData(command, dryRun);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Failed to process E-Trade data from extension", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retrieves all company stock account snapshots.
     *
     * @return A ResponseEntity containing a list of snapshots.
     */
    @Operation(summary = "Get all E-Trade snapshots", description = "Retrieves all company stock snapshots.")
    @GetMapping("/etrade")
    public ResponseEntity<List<CompanyStockResponse.Snapshot>> getAllSnapshots() {
        return ResponseEntity.ok(mapper.toSnapshotResponseList(companyStockService.getAllSnapshots(),
                SecurityUtil.getCurrentUserId()));
    }

    /**
     * Retrieves all current stock lots (ESPP and RSU holdings).
     *
     * @return A ResponseEntity containing a list of stock lots.
     */
    @Operation(summary = "Get all E-Trade stock lots", description = "Retrieves all current stock lots (ESPP/RSU holdings).")
    @GetMapping("/etrade/lots")
    public ResponseEntity<List<CompanyStockResponse.StockLot>> getStockLots() {
        return ResponseEntity.ok(mapper.toStockLotResponseList(companyStockService.getStockLots(),
                SecurityUtil.getCurrentUserId()));
    }

    /**
     * Retrieves all cash transactions (e.g., dividends, transfers).
     *
     * @return A ResponseEntity containing a list of cash transactions.
     */
    @Operation(summary = "Get all E-Trade cash transactions", description = "Retrieves all cash transactions (dividends, transfers, etc).")
    @GetMapping("/etrade/cash-transactions")
    public ResponseEntity<List<CompanyStockResponse.CashTransaction>> getCashTransactions() {
        return ResponseEntity.ok(mapper.toCashTransactionResponseList(companyStockService.getCashTransactions(),
                SecurityUtil.getCurrentUserId()));
    }

    /**
     * Retrieves all ESPP purchases.
     *
     * @return A ResponseEntity containing a list of ESPP purchases.
     */
    @Operation(summary = "Get all ESPP purchases", description = "Retrieves all current ESPP purchases.")
    @GetMapping("/etrade/espp")
    public ResponseEntity<List<CompanyStockResponse.EsppPurchase>> getEsppPurchases() {
        return ResponseEntity.ok(mapper.toEsppPurchaseResponseList(companyStockService.getEsppPurchases(),
                SecurityUtil.getCurrentUserId()));
    }

    /**
     * Retrieves all RSU grants.
     *
     * @return A ResponseEntity containing a list of RSU grants.
     */
    @Operation(summary = "Get all RSU grants", description = "Retrieves all current RSU grants.")
    @GetMapping("/etrade/rsu-grants")
    public ResponseEntity<List<CompanyStockResponse.RsuGrant>> getRsuGrants() {
        return ResponseEntity.ok(mapper.toRsuGrantResponseList(companyStockService.getRsuGrants(),
                SecurityUtil.getCurrentUserId()));
    }

    /**
     * Retrieves all RSU events (e.g., vestings, releases).
     *
     * @return A ResponseEntity containing a list of RSU events.
     */
    @Operation(summary = "Get all RSU events", description = "Retrieves all RSU events.")
    @GetMapping("/etrade/rsu-events")
    public ResponseEntity<List<CompanyStockResponse.RsuEvent>> getRsuEvents() {
        return ResponseEntity.ok(mapper.toRsuEventResponseList(companyStockService.getRsuEvents(),
                SecurityUtil.getCurrentUserId()));
    }

    /**
     * Retrieves all RSU vest schedules.
     *
     * @return A ResponseEntity containing a list of RSU vest schedules.
     */
    @Operation(summary = "Get all RSU vest schedules", description = "Retrieves all RSU vest schedules.")
    @GetMapping("/etrade/rsu-vest-schedules")
    public ResponseEntity<List<CompanyStockResponse.RsuVestSchedule>> getRsuVestSchedules() {
        return ResponseEntity.ok(mapper.toRsuVestScheduleResponseList(companyStockService.getRsuVestSchedules(),
                SecurityUtil.getCurrentUserId()));
    }
}
