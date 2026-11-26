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
package ch.lin.finance.hub.backend.api.app.service.event;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.LifeEventResult;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;

public interface LifeEventService {
    List<LifeEventResult> getAllEvents();

    LifeEventResult createEvent(java.time.YearMonth startYearMonth, java.time.YearMonth endYearMonth,
            LifeEventFrequency frequency, String eventName, Long costPerOccurrence);

    void deleteEvent(Long id);
}
