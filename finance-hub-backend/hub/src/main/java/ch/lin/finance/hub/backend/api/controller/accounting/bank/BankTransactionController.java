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
package ch.lin.finance.hub.backend.api.controller.accounting.bank;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.BankTransactionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.CreateBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankStatementMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankStatementRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankStatementResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankTransactionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankTransactionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankTransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for serving Bank Transactions to the UI.
 */
@Slf4j
@RestController
@RequestMapping("/banks")
@RequiredArgsConstructor
public class BankTransactionController {

    /** Service for retrieving bank transaction data. */
    private final BankTransactionService bankTransactionService;

    /** Mapper for converting between DTOs and internal commands/results. */
    private final BankTransactionMapper bankTransactionMapper;

    /** Mapper for converting entity to DTO. */
    private final BankStatementMapper bankStatementMapper;

    /**
     * Retrieves the latest balances for all banks.
     *
     * @return A map containing bank names as keys and their latest balances as
     *         values.
     */
    @GetMapping("/balances")
    public Map<String, Integer> getBalances() {
        return bankTransactionService.getLatestBankBalances();
    }

    /**
     * Retrieves a paginated list of bank transactions filtered by given criteria.
     *
     * @param bankNames Optional list of bank names to filter by.
     * @param startDate Optional start date for the transactions.
     * @param endDate   Optional end date for the transactions.
     * @param isIncome  Optional flag to filter by income (true) or expense (false).
     * @param pageable  The pagination and sorting information.
     * @return A paginated list of bank transactions matching the criteria.
     */
    @GetMapping("/transactions")
    public Page<BankTransactionResponse> getTransactions(@RequestParam(required = false) List<String> bankNames,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Boolean isIncome,
            Pageable pageable) {

        Page<BankTransactionResult> transactions = bankTransactionService.getTransactions(bankNames, startDate, endDate,
                isIncome, pageable);
        return transactions.map(result -> bankTransactionMapper.toResponse(result, SecurityUtil.getCurrentUserId()));
    }

    /**
     * Adds a new bank transaction manually.
     *
     * @param request The transaction details.
     * @return The created transaction.
     */
    @PostMapping("/transactions")
    public BankTransactionResponse addTransaction(@Valid @RequestBody BankTransactionRequest request) {
        CreateBankTransactionCommand command = bankTransactionMapper.toCommand(request);
        BankTransactionResult created = bankTransactionService.addTransaction(command);
        return bankTransactionMapper.toResponse(created, SecurityUtil.getCurrentUserId());
    }

    /**
     * Receives and processes a bank statement payload.
     *
     * @param request The parsed statement payload.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @PostMapping("/statements")
    public ResponseEntity<BankStatementResponse> receiveBankStatement(
            @Valid @RequestBody BankStatementRequest request) {

        log.info("Received bank statement for bank: {}, month: {}, {} transactions",
                request.getBankName(), request.getStatementMonth(),
                request.getTransactions() != null ? request.getTransactions().size() : 0);

        SaveBankStatementCommand command = bankStatementMapper.toCommand(request);
        BankStatementResult savedResult = bankTransactionService.saveStatement(command);
        BankStatementResponse response = bankStatementMapper.toDto(savedResult);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a bank statement by bank name and statement month.
     *
     * @param bankName       The name of the bank.
     * @param statementMonth The statement month (e.g., "2024-01").
     * @return A ResponseEntity containing the bank statement if found.
     */
    @GetMapping("/statements/{bankName}/{statementMonth}")
    public ResponseEntity<BankStatementResponse> getBankStatement(
            @PathVariable String bankName,
            @PathVariable String statementMonth) {

        log.info("Retrieving bank statement for bank: {}, month: {}", bankName, statementMonth);

        return bankTransactionService.getStatement(bankName, statementMonth)
                .map(bankStatementMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
