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
package ch.lin.finance.hub.backend.api.controller.config.dto.system;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the response for listing all available configurations for a
 * specific transaction source.
 * <p>
 * This DTO is returned by the
 * {@link
 * ch.lin.finance.hub.backend.api.controller.config.TransactionSourceConfigController#getAllConfigs(ch.lin.finance.hub.backend.api.domain.model.TransactionSource)}
 * endpoint. It provides the name of the enabled configuration and a complete
 * list of all configuration names.
 * <p>
 * Example JSON response:
 *
 * <pre>
 * {@code
 * {
 * "enabled": "default",
 * "configs": ["default", "audio-only-config", "4k-video-config"]
 * }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AllConfigsResponse {

    /**
     * The name of the configuration that is currently enabled. Can be null if
     * no configuration is enabled.
     */
    private String enabledConfigName;
    /**
     * A list of names of all available configurations.
     */
    private List<String> allConfigNames;
}
