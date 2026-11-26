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
package ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A Result class that wraps an Amazon Order suggestion and includes contextual flags.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SuggestedMatchResult extends BaseResult {
    private ReconciliationAmazonOrderResult order;
    private boolean isPreOrder;
    private boolean hasMultipleMatches;

    public static SuggestedMatchResult fromEntity(AmazonOrder orderEntity, boolean isPreOrder, boolean hasMultipleMatches) {
        if (orderEntity == null) {
            return null;
        }

        SuggestedMatchResult result = new SuggestedMatchResult();
        // Since AmazonOrder inherits MultiUserBaseEntity, it has ownerUserId and privacyLevel
        result.setOwnerUserId(orderEntity.getOwnerUserId());
        result.setPrivacyLevel(orderEntity.getPrivacyLevel());

        result.setOrder(ReconciliationAmazonOrderResult.fromEntity(orderEntity));
        result.setPreOrder(isPreOrder);
        result.setHasMultipleMatches(hasMultipleMatches);

        return result;
    }
}
