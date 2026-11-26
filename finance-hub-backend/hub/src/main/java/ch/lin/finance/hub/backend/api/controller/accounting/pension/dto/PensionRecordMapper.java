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
package ch.lin.finance.hub.backend.api.controller.accounting.pension.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.pension.command.CreatePensionRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionRecordResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

@Mapper(componentModel = "spring")
public interface PensionRecordMapper {

    CreatePensionRecordCommand toCommand(PensionRecordRequest request);

    PensionRecordResponse toResponse(PensionRecordResult result, @Context String currentUserId);

    PensionProjectionYearlyResponse toResponse(PensionProjectionYearlyResult result, @Context String currentUserId);

    @AfterMapping
    default void applyMasking(@MappingTarget PensionRecordResponse response, PensionRecordResult result, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setBasicPensionAmount(null);
            response.setKouseiPensionAmount(null);
            response.setTotalPensionAmount(null);
            response.setKokuminPremiumPaid(null);
            response.setKouseiPremiumPaid(null);
            response.setTotalPremiumPaid(null);
        }
    }

    default PensionProjectionYearlyResponse toMaskedResponse(PensionProjectionYearlyResult result, @Context String currentUserId) {
        if (result == null) {
            return null;
        }

        PensionProjectionYearlyResponse response = toResponse(result, currentUserId);

        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            return new PensionProjectionYearlyResponse(
                    response.yearsAdditionalWorked(),
                    null,
                    null,
                    null,
                    null
            );
        }

        return response;
    }
}
