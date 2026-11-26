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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc.result;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DefinedContributionAccountSnapshotResult extends BaseResult {
    private Long id;
    private LocalDate recordDate;
    private DefinedContributionType type;
    private Long totalAssetValue;
    private Long totalAccumulatedContribution;
    private Long monthlyEmployerContribution;
    private Long monthlySubscriberContribution;
    private Long monthlyTotalContribution;
    private String remainderFundCode;
    private List<DefinedContributionFundSnapshotResult> funds = new ArrayList<>();

    public static DefinedContributionAccountSnapshotResult fromEntity(DefinedContributionAccountSnapshot entity) {
        if (entity == null) {
            return null;
        }
        DefinedContributionAccountSnapshotResult result = new DefinedContributionAccountSnapshotResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setId(entity.getId());
        result.setRecordDate(entity.getRecordDate());
        result.setType(entity.getType());
        result.setTotalAssetValue(entity.getTotalAssetValue());
        result.setTotalAccumulatedContribution(entity.getTotalAccumulatedContribution());
        result.setMonthlyEmployerContribution(entity.getMonthlyEmployerContribution());
        result.setMonthlySubscriberContribution(entity.getMonthlySubscriberContribution());
        result.setMonthlyTotalContribution(entity.getMonthlyTotalContribution());
        result.setRemainderFundCode(entity.getRemainderFundCode());

        if (entity.getFunds() != null) {
            result.setFunds(entity.getFunds().stream()
                    .map(DefinedContributionFundSnapshotResult::fromEntity)
                    .collect(Collectors.toList()));
        }
        return result;
    }
}
