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

import java.time.LocalDate;
import java.util.List;

import org.springframework.lang.NonNull;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionSnapshotCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;

/**
 * Service for handling Defined Contribution (iDeCo, Corporate DC) data.
 */
public interface DefinedContributionService {

    /**
     * Retrieves all DC snapshot records sorted by record date in descending order.
     *
     * @return A list of all DC snapshot records.
     */
    List<DefinedContributionAccountSnapshotResult> getAllRecords();

    /**
     * Saves a parsed DC snapshot.
     *
     * @param recordDate The record date.
     * @param type       The type of the DC account.
     * @param snapshot   The snapshot to save.
     * @param dryRun     If true, the snapshot is not saved.
     */
    void saveSnapshot(SaveDefinedContributionSnapshotCommand command, boolean dryRun);

    /**
     * Updates the expected return rate for a specific DC fund snapshot.
     *
     * @param fundId  The ID of the DC fund snapshot.
     * @param command The update command (only expectedReturnRate is applied).
     * @return The updated fund record.
     */
    DefinedContributionFundSnapshotResult updateAllocation(@NonNull Long fundId, UpdateAllocationCommand command);

    /**
     * Batch-updates allocation percentages and the remainder-absorbing fund for
     * an entire DC account snapshot, recalculating each fund's monthly
     * contribution amount from {@code monthlyTotalContribution}.
     *
     * @param accountId The ID of the DC account snapshot.
     * @param command   The batch allocation update command.
     * @return The updated account snapshot record.
     */
    DefinedContributionAccountSnapshotResult updateAccountAllocations(@NonNull Long accountId,
            UpdateAccountAllocationsCommand command);

    /**
     * Deletes a specific DC snapshot by its ID.
     *
     * @param id The ID of the DC account snapshot.
     */
    void deleteSnapshotById(@NonNull Long id);

    /**
     * Deletes all DC snapshots for a specific record date.
     *
     * @param recordDate The record date to delete snapshots for.
     */
    void deleteSnapshotByDate(@NonNull LocalDate recordDate);

    /**
     * Deletes all DC snapshots and resets the auto-increment sequence.
     */
    void deleteAllSnapshots();
}
