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
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEventType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CareerEventResult extends BaseResult {
    private final Long id;
    private final java.time.YearMonth eventYearMonth;
    private final String eventName;
    private final CareerEventType eventType;
    private final Double eventValue;

    public static CareerEventResult fromEntity(CareerEvent entity) {
        if (entity == null) {
            return null;
        }
        CareerEventResult result = CareerEventResult.builder()
                .id(entity.getId())
                .eventYearMonth(entity.getEventYearMonth())
                .eventName(entity.getEventName())
                .eventType(entity.getEventType())
                .eventValue(entity.getEventValue())
                .build();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        return result;
    }
}
