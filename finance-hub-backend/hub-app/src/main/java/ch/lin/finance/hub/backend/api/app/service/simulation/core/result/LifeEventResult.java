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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LifeEventResult extends BaseResult {
    private final Long id;
    private final java.time.YearMonth startYearMonth;
    private final java.time.YearMonth endYearMonth;
    private final LifeEventFrequency frequency;
    private final String eventName;
    private final Long costPerOccurrence;

    public static LifeEventResult fromEntity(LifeEvent entity) {
        if (entity == null) {
            return null;
        }
        LifeEventResult result = LifeEventResult.builder()
                .id(entity.getId())
                .startYearMonth(entity.getStartYearMonth())
                .endYearMonth(entity.getEndYearMonth())
                .frequency(entity.getFrequency())
                .eventName(entity.getEventName())
                .costPerOccurrence(entity.getCostPerOccurrence())
                .build();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        return result;
    }
}
