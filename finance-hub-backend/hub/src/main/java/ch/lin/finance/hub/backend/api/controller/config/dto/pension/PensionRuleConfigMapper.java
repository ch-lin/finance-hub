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
package ch.lin.finance.hub.backend.api.controller.config.dto.pension;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ch.lin.finance.hub.backend.api.app.service.config.pension.command.CreatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.UpdatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;

/**
 * Mapper for PensionRuleConfig DTOs.
 */
@Mapper(componentModel = "spring")
public interface PensionRuleConfigMapper {
    PensionRuleConfigResponse toResponse(PensionRuleConfigResult result);

    @Mapping(target = "enabled", defaultValue = "false")
    CreatePensionRuleConfigCommand toCommand(CreatePensionRuleConfigRequest request);

    UpdatePensionRuleConfigCommand toCommand(UpdatePensionRuleConfigRequest request);
}
