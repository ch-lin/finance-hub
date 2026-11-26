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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.command;

import java.time.LocalDate;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import lombok.Data;

@Data
public class SaveDefinedContributionSnapshotCommand {
    private LocalDate recordDate;
    private DefinedContributionType type;
    private long totalAssetValue = 0L;
    private long totalAccumulatedContribution = 0L;
    private long monthlyEmployerContribution = 0L;
    private long monthlySubscriberContribution = 0L;
    private long monthlyTotalContribution = 0L;
    private List<SaveDefinedContributionFundSnapshotCommand> funds;
}
