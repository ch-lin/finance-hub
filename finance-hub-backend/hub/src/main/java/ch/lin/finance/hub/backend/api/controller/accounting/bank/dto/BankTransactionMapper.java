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
package ch.lin.finance.hub.backend.api.controller.accounting.bank.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.CreateBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

/**
 * Mapper for converting between Bank Transaction DTOs and internal
 * commands/results.
 */
@Mapper(componentModel = "spring")
public interface BankTransactionMapper {

    /**
     * Converts a BankTransactionRequest to a CreateBankTransactionCommand.
     *
     * @param request The incoming transaction request.
     * @return The internal command to create the transaction.
     */
    CreateBankTransactionCommand toCommand(BankTransactionRequest request);

    /**
     * Converts a BankTransactionResult to a BankTransactionResponse.
     *
     * @param result        The internal transaction result.
     * @param currentUserId The ID of the currently authenticated user for masking.
     * @return The response DTO containing the transaction details.
     */
    BankTransactionResponse toResponse(BankTransactionResult result, @Context String currentUserId);

    /**
     * Applies privacy masking to the response DTO if necessary based on user
     * permissions.
     *
     * @param response      The target response DTO to be potentially masked.
     * @param result        The internal transaction result containing privacy
     *                      rules.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyMasking(@MappingTarget BankTransactionResponse response, BankTransactionResult result,
            @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setDescription(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.BANK));
            response.setMemo(null);
        }
    }
}
