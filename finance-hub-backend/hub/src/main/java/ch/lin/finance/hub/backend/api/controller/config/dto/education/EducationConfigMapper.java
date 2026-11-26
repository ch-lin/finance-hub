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
package ch.lin.finance.hub.backend.api.controller.config.dto.education;

import java.util.List;

import org.mapstruct.Mapper;

import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.CreateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.UpdateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.result.EducationConfigResult;

/**
 * MapStruct mapper for EducationConfig DTOs.
 */
@Mapper(componentModel = "spring")
public interface EducationConfigMapper {

    // --- Result to Response ---

    EducationConfigResponse toResponse(EducationConfigResult result);

    List<EducationConfigResponse> toResponseList(List<EducationConfigResult> results);

    PreschoolConfigResponse toResponse(EducationConfigResult.PreschoolConfigResult result);

    PrimaryConfigResponse toResponse(EducationConfigResult.PrimaryConfigResult result);

    MiddleConfigResponse toResponse(EducationConfigResult.MiddleConfigResult result);

    HighConfigResponse toResponse(EducationConfigResult.HighConfigResult result);

    UniversityConfigResponse toResponse(EducationConfigResult.UniversityConfigResult result);

    TrackConfigResponse toResponse(EducationConfigResult.TrackConfigResult result);

    // --- Request to Command / Properties ---

    CreateEducationConfigCommand toCommand(CreateEducationConfigRequest request);

    UpdateEducationConfigCommand toCommand(UpdateEducationConfigRequest request);

    EducationConfigDefaultProperties.PreschoolConfigProperties toProperties(PreschoolConfigRequest request);

    EducationConfigDefaultProperties.PrimaryConfigProperties toProperties(PrimaryConfigRequest request);

    EducationConfigDefaultProperties.MiddleConfigProperties toProperties(MiddleConfigRequest request);

    EducationConfigDefaultProperties.HighConfigProperties toProperties(HighConfigRequest request);

    EducationConfigDefaultProperties.UniversityConfigProperties toProperties(UniversityConfigRequest request);

    EducationConfigDefaultProperties.TrackConfigProperty toProperties(TrackConfigRequest request);
}
