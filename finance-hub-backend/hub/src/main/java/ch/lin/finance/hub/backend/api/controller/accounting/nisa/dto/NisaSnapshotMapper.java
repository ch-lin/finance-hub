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
package ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.UpdateNisaReturnRateCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaSnapshotRecordResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

@Mapper(componentModel = "spring")
public interface NisaSnapshotMapper {

    UpdateNisaReturnRateCommand toCommand(UpdateNisaReturnRateRequest request);

    NisaSnapshotRecordResponse toResponse(NisaSnapshotRecordResult result, @Context String currentUserId);

    NisaProjectionYearlyResponse toResponse(NisaProjectionYearlyResult result, @Context String currentUserId);

    @AfterMapping
    default void applyMasking(@MappingTarget NisaSnapshotRecordResponse response, NisaSnapshotRecordResult result,
            @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setFundCode(null);
            response.setFundName(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.NISA));
            response.setUnits(null);
            response.setAverageCost(null);
            response.setCurrentPrice(null);
            response.setTotalPrincipal(null);
            response.setCurrentMarketValue(null);
            response.setUnrealizedGainLoss(null);
            response.setExpectedReturnRate(null);
        }
    }

    default NisaProjectionYearlyResponse toMaskedResponse(NisaProjectionYearlyResult result,
            @Context String currentUserId) {
        if (result == null) {
            return null;
        }

        NisaProjectionYearlyResponse response = toResponse(result, currentUserId);

        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            return new NisaProjectionYearlyResponse(
                    response.year(),
                    null,
                    null,
                    null);
        }

        return response;
    }
}
