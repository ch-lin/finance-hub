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
package ch.lin.finance.hub.backend.api.controller.accounting.reconciliation.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.command.LinkTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.ReconciliationAmazonOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.SuggestedMatchResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.creditcard.dto.CreditCardTransactionResponse;

@Mapper(componentModel = "spring")
public interface ReconciliationMapper {

    LinkTransactionCommand toCommand(LinkTransactionRequest request);

    CreditCardTransactionResponse toCreditCardTransactionResponse(CreditCardTransactionResult result,
            @Context String currentUserId);

    @Mapping(target = "dPointsUsed", source = "DPointsUsed")
    AmazonOrderResponse toAmazonOrderResponse(ReconciliationAmazonOrderResult result, @Context String currentUserId);

    @Mapping(target = "order", source = "order")
    @Mapping(target = "isPreOrder", source = "preOrder")
    SuggestedMatchResponse toSuggestedMatchResponse(SuggestedMatchResult result, @Context String currentUserId);

    @AfterMapping
    default void applyCreditCardMasking(@MappingTarget CreditCardTransactionResponse response,
            CreditCardTransactionResult result, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setStoreName(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.CREDIT_CARD));
            response.setMemo(null);
        }
    }

    @AfterMapping
    default void applyAmazonOrderMasking(@MappingTarget AmazonOrderResponse response,
            ReconciliationAmazonOrderResult result, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setReviewReason(null);
        }
    }
}
