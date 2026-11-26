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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for creating or updating a category.
 *
 * @param name                  The name of the category.
 * @param isEssential           Indicates whether this category is considered
 *                              essential.
 * @param isSimulationDuplicate Indicates whether this category is a duplicate
 *                              used in simulations.
 * @param parentId              The unique identifier of the parent category, if
 *                              any.
 */
public record CategoryRequest(
        @NotBlank(message = "Name is required") String name,
        @NotNull(message = "isEssential is required") Boolean isEssential,
        @NotNull(message = "isSimulationDuplicate is required") Boolean isSimulationDuplicate,
        Long parentId) {
}
