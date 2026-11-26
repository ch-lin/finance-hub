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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MortgageEarlyRepaymentResult extends BaseResult {

    private Long id;
    private LocalDate repaymentDate;
    private Long amount;
    private EarlyRepaymentType repaymentType;
    private Long fee;
    private Integer reducedMonths;

    public static MortgageEarlyRepaymentResult fromEntity(MortgageEarlyRepayment entity) {
        if (entity == null) {
            return null;
        }
        MortgageEarlyRepaymentResult dto = new MortgageEarlyRepaymentResult();
        dto.setId(entity.getId());
        dto.setRepaymentDate(entity.getRepaymentDate());
        dto.setAmount(entity.getAmount());
        dto.setRepaymentType(entity.getRepaymentType());
        dto.setFee(entity.getFee());
        dto.setReducedMonths(entity.getReducedMonths());
        dto.setOwnerUserId(entity.getOwnerUserId());
        dto.setPrivacyLevel(entity.getPrivacyLevel());
        return dto;
    }
}
