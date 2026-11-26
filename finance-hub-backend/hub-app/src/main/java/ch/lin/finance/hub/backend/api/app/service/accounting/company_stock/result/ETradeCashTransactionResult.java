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
import ch.lin.finance.hub.backend.api.app.service.base.MonetaryValue;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeCashTransaction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeCashTransactionResult extends BaseResult {
    private Long id; // From ETradeCashTransaction.id (BaseEntity) -> CompanyStockResponse.CashTransaction.id
    private LocalDate transactionDate; // From ETradeCashTransaction.transactionDate -> CompanyStockResponse.CashTransaction.transactionDate
    private String description; // From ETradeCashTransaction.description -> CompanyStockResponse.CashTransaction.description
    private MonetaryValue amount; // From ETradeCashTransaction.amount -> CompanyStockResponse.CashTransaction.amount

    public static ETradeCashTransactionResult fromEntity(ETradeCashTransaction entity) {
        if (entity == null) {
            return null;
        }
        ETradeCashTransactionResult result = new ETradeCashTransactionResult();
        result.setId(entity.getId());
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setTransactionDate(entity.getTransactionDate());
        result.setDescription(entity.getDescription());
        result.setAmount(MonetaryValue.fromEntity(entity.getAmount()));
        return result;
    }
}

