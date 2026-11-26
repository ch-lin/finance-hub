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
package ch.lin.finance.hub.backend.api.app.service.config.system.result;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A data transfer object (DTO) that holds information about hub configurations.
 * <p>
 * This class encapsulates the name of the currently enabled configuration and a
 * list of all available configuration names. It is typically used to pass this
 * data from the service layer to the presentation layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AllConfigsData {

    /**
     * The name of the currently active/enabled configuration. Can be null if
     * none are enabled.
     */
    private String enabledConfigName;

    /**
     * A list of names for all existing configurations.
     */
    private List<String> allConfigNames;

}
