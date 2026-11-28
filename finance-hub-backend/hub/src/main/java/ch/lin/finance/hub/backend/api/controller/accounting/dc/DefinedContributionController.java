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
package ch.lin.finance.hub.backend.api.controller.accounting.dc;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionJobServiceImpl;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.DefinedContributionService;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DefinedContributionAccountSnapshotResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DefinedContributionFundSnapshotResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DefinedContributionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DefinedContributionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * REST controller for managing Defined Contribution (iDeCo, Corporate DC)
 * snapshot records.
 */
@RestController
@RequestMapping("/assets/dc")
public class DefinedContributionController {

    private static final Logger logger = LoggerFactory.getLogger(DefinedContributionController.class);

    private final DefinedContributionService dcService;
    private final DefinedContributionJobServiceImpl dcJobService;
    private final DefinedContributionMapper dcMapper;

    public DefinedContributionController(DefinedContributionService dcService,
            DefinedContributionJobServiceImpl dcJobService, DefinedContributionMapper dcMapper) {
        this.dcService = dcService;
        this.dcJobService = dcJobService;
        this.dcMapper = dcMapper;
    }

    /**
     * Retrieves all DC snapshot records sorted by record date in descending order.
     *
     * @return A list of all DC snapshot records.
     */
    @GetMapping
    public List<DefinedContributionAccountSnapshotResponse> getAllRecords() {
        String currentUserId = SecurityUtil.getCurrentUserId();
        return dcService.getAllRecords().stream().map(result -> dcMapper.toResponse(result, currentUserId)).toList();
    }

    /**
     * Uploads and parses a new DC HTML file.
     *
     * @param file The HTML file to upload.
     * @return The processing result containing the parsed snapshot.
     */
    @PostMapping("/upload")
    public FileProcessingResult uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        try {
            File tempFile = File.createTempFile("dc-upload-", ".html");
            file.transferTo(Objects.requireNonNull(tempFile));

            // By default, dryRun is false to save it immediately for the upload endpoint.
            FileProcessingResult result = dcJobService.processFile(tempFile, false, SecurityUtil.getCurrentUserId());

            if (!tempFile.delete()) {
                logger.warn("Could not delete temporary file: {}", tempFile.getAbsolutePath());
            }

            return result;
        } catch (IOException e) {
            logger.error("Failed to save uploaded file", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process file upload", e);
        } catch (Exception e) {
            logger.error("Failed to parse uploaded DC file", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse file: " + e.getMessage(), e);
        }
    }

    /**
     * Receives parsed DC data in JSON format and creates a snapshot.
     *
     * @param request The parsed DC payload.
     * @param dryRun  If true, only parses and logs the data without saving.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @PostMapping
    public ResponseEntity<Void> createSnapshotFromJson(@RequestBody DefinedContributionRequest request,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {

        logger.info("Received DC data for date: {}, dryRun: {}", request.recordDate(), dryRun);
        try {
            var command = dcMapper.toSaveSnapshotCommand(request);
            dcService.saveSnapshot(command, dryRun);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Failed to process DC data", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deletes DC snapshots. If recordDate is provided, deletes snapshots for that
     * date.
     * Otherwise, deletes all snapshots and resets the sequence.
     *
     * @param recordDate Optional date to filter the deletion.
     * @return A ResponseEntity indicating the result.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteSnapshots(
            @RequestParam(value = "recordDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate recordDate) {
        if (recordDate != null) {
            dcService.deleteSnapshotByDate(recordDate);
        } else {
            dcService.deleteAllSnapshots();
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Deletes a specific DC snapshot by its ID.
     *
     * @param id The ID of the DC account snapshot.
     * @return A ResponseEntity indicating the result.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSnapshotById(@PathVariable @NonNull Long id) {
        dcService.deleteSnapshotById(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Updates the expected return rate for a specific DC fund snapshot.
     *
     * @param fundId  The ID of the DC fund snapshot.
     * @param request The request containing the new expected return rate.
     * @return The updated fund record.
     */
    @PatchMapping("/funds/{fundId}/allocation")
    public DefinedContributionFundSnapshotResponse updateAllocation(@PathVariable @NonNull Long fundId,
            @RequestBody @Valid UpdateExpectedReturnRateRequest request) {

        var command = dcMapper.toUpdateAllocationCommand(request);
        return dcMapper.toResponse(dcService.updateAllocation(fundId, command), SecurityUtil.getCurrentUserId());
    }

    /**
     * Batch-updates allocation percentages and the remainder-absorbing fund for
     * a DC account snapshot, recalculating monthly contribution amounts.
     *
     * @param accountId The ID of the DC account snapshot.
     * @param request   The batch allocation request.
     * @return The updated account snapshot record.
     */
    @PatchMapping("/accounts/{accountId}/allocations")
    public DefinedContributionAccountSnapshotResponse updateAccountAllocations(
            @PathVariable @NonNull Long accountId,
            @RequestBody @Valid UpdateAccountAllocationsRequest request) {

        var command = dcMapper.toUpdateAccountAllocationsCommand(request);
        return dcMapper.toResponse(dcService.updateAccountAllocations(accountId, command),
                SecurityUtil.getCurrentUserId());
    }

    public record UpdateExpectedReturnRateRequest(
            @DecimalMin("-1.0") @DecimalMax("1.0") Double expectedReturnRate) {
    }

    public record UpdateAccountAllocationsRequest(
            @NotBlank String remainderFundCode,
            @NotEmpty @Valid List<FundAllocationRequest> allocations) {
    }

    public record FundAllocationRequest(
            @NotBlank String fundCode,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double allocationPercentage) {
    }
}
