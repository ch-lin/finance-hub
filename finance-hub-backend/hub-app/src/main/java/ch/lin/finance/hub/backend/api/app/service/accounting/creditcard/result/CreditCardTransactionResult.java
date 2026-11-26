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
package ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreditCardTransactionResult extends BaseResult {
    private Long id;
    private String cardName;
    private LocalDate transactionDate;
    private String storeName;
    private Integer amount;
    private String memo;

    public static CreditCardTransactionResult fromEntity(CreditCardTransaction entity) {
        if (entity == null) {
            return null;
        }
        CreditCardTransactionResult result = new CreditCardTransactionResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setId(entity.getId());
        result.setCardName(entity.getCardName());
        result.setTransactionDate(entity.getTransactionDate());
        result.setStoreName(entity.getStoreName());
        result.setAmount(entity.getAmount());
        result.setMemo(entity.getMemo());
        return result;
    }
}
