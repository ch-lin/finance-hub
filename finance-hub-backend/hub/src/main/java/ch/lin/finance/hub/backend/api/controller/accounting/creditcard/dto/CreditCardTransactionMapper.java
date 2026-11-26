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
package ch.lin.finance.hub.backend.api.controller.accounting.creditcard.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.command.CreateCreditCardTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

@Mapper(componentModel = "spring")
public interface CreditCardTransactionMapper {

    CreateCreditCardTransactionCommand toCommand(CreditCardTransactionRequest request);

    CreditCardTransactionResponse toResponse(CreditCardTransactionResult result, @Context String currentUserId);

    @AfterMapping
    default void applyMasking(CreditCardTransactionResult result, @MappingTarget CreditCardTransactionResponse response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setStoreName(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.CREDIT_CARD));
            response.setAmount(null);
            response.setMemo(null);
        }
    }
}
