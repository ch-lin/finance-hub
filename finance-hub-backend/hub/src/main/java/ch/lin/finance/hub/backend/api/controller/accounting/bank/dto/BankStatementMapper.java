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

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;

/**
 * Mapper for converting between Bank Statement DTOs and internal
 * commands/results.
 */
@Mapper(componentModel = "spring")
public interface BankStatementMapper {

    /**
     * Converts a BankStatementResult to a BankStatementResponse.
     *
     * @param result The internal statement result.
     * @return The response DTO.
     */
    BankStatementResponse toDto(BankStatementResult result);

    /**
     * Converts a BankTransactionResult to a BankStatementTransactionResponse.
     *
     * @param result The internal transaction result.
     * @return The response DTO.
     */
    BankStatementTransactionResponse toDto(BankTransactionResult result);

    /**
     * Converts a BankStatementRequest to a SaveBankStatementCommand.
     *
     * @param request The incoming statement request.
     * @return The internal command.
     */
    SaveBankStatementCommand toCommand(BankStatementRequest request);

    /**
     * Converts a BankStatementTransactionRequest to a SaveBankTransactionCommand.
     *
     * @param request The incoming transaction request.
     * @return The internal command.
     */
    @Mapping(target = "transactionDate", source = "request.transactionDate")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "depositAmount", source = "request.depositAmount")
    @Mapping(target = "withdrawalAmount", source = "request.withdrawalAmount")
    @Mapping(target = "balance", source = "request.balance")
    @Mapping(target = "memo", source = "request.memo")
    SaveBankTransactionCommand toCommand(BankStatementTransactionRequest request);
}
