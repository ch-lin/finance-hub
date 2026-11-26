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
package ch.lin.finance.hub.backend.api.controller.accounting.salary.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.salary.command.SaveSalaryRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.result.SalaryRecordResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

@Mapper(componentModel = "spring")
public interface SalaryRecordMapper {
    SalaryRecordResponse toResponse(SalaryRecordResult result, @Context String currentUserId);

    SaveSalaryRecordCommand toCommand(SalaryRecordRequest request);

    @AfterMapping
    default void applyMasking(@MappingTarget SalaryRecordResponse response, SalaryRecordResult result,
            @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setNote(null);
            response.setDetails(null);
        }
    }
}
