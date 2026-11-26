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
package ch.lin.finance.hub.backend.api.controller.accounting.category.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.category.command.SaveCategoryCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.result.TransactionCategoryResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

/**
 * Mapper for converting between category DTOs and domain commands/results.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper {
    /**
     * Converts a CategoryRequest DTO to a SaveCategoryCommand.
     *
     * @param request The category request containing category details.
     * @return The corresponding SaveCategoryCommand.
     */
    SaveCategoryCommand toCommand(CategoryRequest request);

    /**
     * Converts a TransactionCategoryResult to a CategoryResponse DTO.
     *
     * @param result        The domain result object.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The CategoryResponse DTO.
     */
    @Mapping(target = "isEssential", source = "essential")
    @Mapping(target = "isSimulationDuplicate", source = "simulationDuplicate")
    CategoryResponse toResponse(TransactionCategoryResult result, @Context String currentUserId);

    /**
     * Applies privacy masking to the category name if the current user is not
     * authorized
     * to view the full details based on the privacy level.
     *
     * @param response      The mapped CategoryResponse.
     * @param result        The original TransactionCategoryResult.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyMasking(@MappingTarget CategoryResponse response, TransactionCategoryResult result,
            @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setName(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.CATEGORY));
        }
    }
}
