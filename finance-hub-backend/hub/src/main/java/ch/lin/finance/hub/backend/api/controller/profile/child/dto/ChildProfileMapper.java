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
package ch.lin.finance.hub.backend.api.controller.profile.child.dto;

import org.mapstruct.Mapper;

import ch.lin.finance.hub.backend.api.app.service.profile.child.command.CreateChildProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.command.UpdateChildProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.result.ChildProfileResult;
import ch.lin.finance.hub.backend.api.app.service.profile.child.result.EducationProjectionResult;

@Mapper(componentModel = "spring")
public interface ChildProfileMapper {

    ChildProfileDto toDto(ChildProfileResult result);

    EducationProjectionDto toDto(EducationProjectionResult result);

    CreateChildProfileCommand toCommand(CreateChildProfileRequest request);

    UpdateChildProfileCommand toCommand(UpdateChildProfileRequest request);
}
