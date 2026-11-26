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
package ch.lin.finance.hub.backend.api.controller.accounting.dc.dto;

import java.util.List;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionFundSnapshotCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionSnapshotCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionController.FundAllocationRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionController.UpdateAccountAllocationsRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionController.UpdateExpectedReturnRateRequest;

@Mapper(componentModel = "spring")
public interface DefinedContributionMapper {

    @Mapping(target = "recordDate", dateFormat = "yyyy-MM-dd")
    SaveDefinedContributionSnapshotCommand toSaveSnapshotCommand(DefinedContributionRequest request);

    SaveDefinedContributionFundSnapshotCommand toFundSnapshotCommand(DefinedContributionFundRequest request);

    UpdateAllocationCommand toUpdateAllocationCommand(UpdateExpectedReturnRateRequest request);

    UpdateAccountAllocationsCommand toUpdateAccountAllocationsCommand(UpdateAccountAllocationsRequest request);

    FundAllocationCommand toFundAllocationCommand(FundAllocationRequest request);

    DefinedContributionAccountSnapshotResponse toResponse(DefinedContributionAccountSnapshotResult result,
            @Context String currentUserId);

    DefinedContributionFundSnapshotResponse toResponse(DefinedContributionFundSnapshotResult result,
            @Context String currentUserId);

    List<DefinedContributionFundSnapshotResponse> toFundResponseList(
            List<DefinedContributionFundSnapshotResult> results, @Context String currentUserId);

    @AfterMapping
    default void applyAccountSnapshotMasking(DefinedContributionAccountSnapshotResult result,
            @MappingTarget DefinedContributionAccountSnapshotResponse response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setTotalAssetValue(null);
            response.setTotalAccumulatedContribution(null);
            response.setMonthlyEmployerContribution(null);
            response.setMonthlySubscriberContribution(null);
            response.setMonthlyTotalContribution(null);
            response.setRemainderFundCode(null);
        }
    }

    @AfterMapping
    default void applyFundSnapshotMasking(DefinedContributionFundSnapshotResult result,
            @MappingTarget DefinedContributionFundSnapshotResponse response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setFundName(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.DC));
            response.setFundCode(null);
            response.setCommodityType(null);
            response.setUnits(null);
            response.setAverageCost(null);
            response.setCurrentPrice(null);
            response.setTotalPrincipal(null);
            response.setCurrentMarketValue(null);
            response.setUnrealizedGainLoss(null);
            response.setExpectedReturnRate(null);
            response.setMonthlyContributionAmount(null);
            response.setAllocationPercentage(null);
        }
    }
}
