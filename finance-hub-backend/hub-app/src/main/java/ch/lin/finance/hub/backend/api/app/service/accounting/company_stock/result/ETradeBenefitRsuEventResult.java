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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeBenefitRsuEventResult extends BaseResult {
    private Long id; // From ETradeBenefitRsuEvent.id (BaseEntity) -> CompanyStockResponse.RsuEvent.id
    private String grantNumber; // From ETradeBenefitRsuEvent.grantNumber -> CompanyStockResponse.RsuEvent.grantNumber
    private LocalDate eventDate; // From ETradeBenefitRsuEvent.eventDate -> CompanyStockResponse.RsuEvent.eventDate
    private String eventType; // From ETradeBenefitRsuEvent.eventType -> CompanyStockResponse.RsuEvent.eventType
    private Double quantity; // From ETradeBenefitRsuEvent.quantity -> CompanyStockResponse.RsuEvent.quantity

    public static ETradeBenefitRsuEventResult fromEntity(ETradeBenefitRsuEvent entity) {
        if (entity == null) {
            return null;
        }
        ETradeBenefitRsuEventResult result = new ETradeBenefitRsuEventResult();
        result.setId(entity.getId());
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setGrantNumber(entity.getGrantNumber());
        result.setEventDate(entity.getEventDate());
        result.setEventType(entity.getEventType());
        result.setQuantity(entity.getQuantity());
        return result;
    }
}

