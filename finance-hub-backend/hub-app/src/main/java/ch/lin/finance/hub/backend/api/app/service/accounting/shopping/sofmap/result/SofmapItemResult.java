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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result;

import java.math.BigDecimal;

import ch.lin.finance.hub.backend.api.app.service.base.PrivacyOverrideBaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SofmapItemResult extends PrivacyOverrideBaseResult {
    private String name;
    private String itemId;
    private String seller;
    private BigDecimal unitPrice;
    private Integer quantity;

    public static SofmapItemResult fromEntity(SofmapItem entity) {
        if (entity == null)
            return null;
        SofmapItemResult result = new SofmapItemResult(
                entity.getName(),
                entity.getItemId(),
                entity.getSeller(),
                entity.getUnitPrice(),
                entity.getQuantity());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        return result;
    }
}
