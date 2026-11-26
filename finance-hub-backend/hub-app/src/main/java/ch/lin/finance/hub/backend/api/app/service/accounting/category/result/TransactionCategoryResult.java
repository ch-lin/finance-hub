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
package ch.lin.finance.hub.backend.api.app.service.accounting.category.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionCategoryResult extends BaseResult {
    private Long id;
    private String name;
    private boolean isEssential;
    private boolean isSimulationDuplicate;
    private Long parentId;

    public static TransactionCategoryResult fromEntity(TransactionCategory entity) {
        if (entity == null) {
            return null;
        }
        TransactionCategoryResult result = new TransactionCategoryResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setId(entity.getId());
        result.setName(entity.getName());
        result.setEssential(entity.isEssential());
        result.setSimulationDuplicate(entity.isSimulationDuplicate());
        if (entity.getParent() != null) {
            result.setParentId(entity.getParent().getId());
        }
        return result;
    }
}
