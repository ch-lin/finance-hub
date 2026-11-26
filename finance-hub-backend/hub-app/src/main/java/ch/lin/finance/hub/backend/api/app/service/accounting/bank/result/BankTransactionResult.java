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

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BankTransactionResult extends BaseResult {
    private Long id;
    private String bankName;
    private LocalDate transactionDate;
    private String description;
    private Integer depositAmount;
    private Integer withdrawalAmount;
    private Integer balance;
    private String memo;
    private Long categoryId;

    public static BankTransactionResult fromEntity(BankTransaction entity) {
        if (entity == null) {
            return null;
        }
        BankTransactionResult result = new BankTransactionResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setId(entity.getId());
        result.setBankName(entity.getBankName());
        result.setTransactionDate(entity.getTransactionDate());
        result.setDescription(entity.getDescription());
        result.setDepositAmount(entity.getDepositAmount());
        result.setWithdrawalAmount(entity.getWithdrawalAmount());
        result.setBalance(entity.getBalance());
        result.setMemo(entity.getMemo());
        if (entity.getCategory() != null) {
            result.setCategoryId(entity.getCategory().getId());
        }
        return result;
    }
}
