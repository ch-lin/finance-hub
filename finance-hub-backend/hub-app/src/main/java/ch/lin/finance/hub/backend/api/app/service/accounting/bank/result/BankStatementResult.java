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
package ch.lin.finance.hub.backend.api.app.service.accounting.bank.result;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankStatement;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BankStatementResult extends BaseResult {
    private Long id;
    private String bankName;
    private String statementMonth;
    private List<BankTransactionResult> transactions = new ArrayList<>();

    public static BankStatementResult fromEntity(BankStatement entity) {
        if (entity == null) {
            return null;
        }
        BankStatementResult result = new BankStatementResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setId(entity.getId());
        result.setBankName(entity.getBankName());
        result.setStatementMonth(entity.getStatementMonth());
        if (entity.getTransactions() != null) {
            result.setTransactions(entity.getTransactions().stream()
                    .map(BankTransactionResult::fromEntity)
                    .collect(Collectors.toList()));
        }
        return result;
    }
}
