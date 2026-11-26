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

import lombok.Builder;
import lombok.Data;

/**
 * Response body for a category.
 */
@Data
@Builder
public class CategoryResponse {
    /** The unique identifier of the category. */
    private Long id;

    /** The name of the category. */
    private String name;

    /** Indicates whether this category is considered essential. */
    private boolean isEssential;

    /** Indicates whether this category is a duplicate used in simulations. */
    private boolean isSimulationDuplicate;

    /** The unique identifier of the parent category, if any. */
    private Long parentId;
}
