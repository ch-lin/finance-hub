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
package ch.lin.finance.hub.backend.api.controller.event.dto;

import java.time.YearMonth;

import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;

/**
 * Data Transfer Object representing a request to create or update a Life Event.
 */
public record LifeEventRequest(
        YearMonth startYearMonth,
        YearMonth endYearMonth,
        LifeEventFrequency frequency,
        String eventName,
        Long costPerOccurrence) {
}
