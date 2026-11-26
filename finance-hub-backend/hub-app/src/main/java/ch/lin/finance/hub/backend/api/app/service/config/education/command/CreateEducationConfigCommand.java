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
package ch.lin.finance.hub.backend.api.app.service.config.education.command;

import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.HighConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.MiddleConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.PreschoolConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.PrimaryConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.UniversityConfigProperties;
import lombok.Builder;
import lombok.Value;

/**
 * Command DTO for creating a new EducationConfig.
 */
@Value
@Builder
public class CreateEducationConfigCommand {
    String name;
    PreschoolConfigProperties preschool;
    PrimaryConfigProperties primary;
    MiddleConfigProperties middle;
    HighConfigProperties high;
    UniversityConfigProperties university;
}
