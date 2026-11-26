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

import org.mapstruct.Mapper;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.SaveNisaUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaUserSettingsResult;

@Mapper(componentModel = "spring")
public interface NisaUserSettingsMapper {
    NisaUserSettingsResponse toResponse(NisaUserSettingsResult result);

    SaveNisaUserSettingsCommand toCommand(NisaUserSettingsRequest request);
}
