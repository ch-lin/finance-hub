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
package ch.lin.finance.hub.backend.api.controller.accounting.creditcard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardTransactionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.command.CreateCreditCardTransactionCommand;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.creditcard.dto.CreditCardTransactionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.creditcard.dto.CreditCardTransactionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.creditcard.dto.CreditCardTransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST Controller for serving Credit Card Transactions to the UI.
 */
@RestController
@RequestMapping("/credit-cards")
@RequiredArgsConstructor
public class CreditCardTransactionController {

    /** Service for managing credit card transactions. */
    private final CreditCardTransactionService creditCardTransactionService;
    private final CreditCardTransactionMapper creditCardTransactionMapper;

    /**
     * Retrieves a paginated list of credit card transactions matching the given
     * criteria.
     *
     * @param cardNames Optional list of card names to filter by.
     * @param startDate Optional start date for the transactions.
     * @param endDate   Optional end date for the transactions.
     * @param pageable  The pagination and sorting information.
     * @return A paginated list of credit card transactions.
     */
    @GetMapping("/transactions")
    public Page<CreditCardTransactionResponse> getTransactions(
            @RequestParam(required = false) List<String> cardNames,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {

        String currentUserId = SecurityUtil.getCurrentUserId();
        return creditCardTransactionService.getTransactions(cardNames, startDate, endDate, pageable)
                .map(result -> creditCardTransactionMapper.toResponse(result, currentUserId));
    }

    /**
     * Retrieves a list of all available credit card names.
     *
     * @return A list of card names.
     */
    @GetMapping
    public List<String> getAvailableCardNames() {
        return creditCardTransactionService.getAvailableCardNames();
    }

    /**
     * Retrieves the total amounts grouped by card name for a given period.
     *
     * @param cardNames Optional list of card names to filter by.
     * @param startDate Optional start date for the transactions.
     * @param endDate   Optional end date for the transactions.
     * @return A map of card names to their corresponding total amounts.
     */
    @GetMapping("/total-amounts")
    public Map<String, Integer> getTotalAmounts(
            @RequestParam(required = false) List<String> cardNames,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return creditCardTransactionService.getTotalAmounts(cardNames, startDate, endDate);
    }

    /**
     * Adds a new credit card transaction manually.
     *
     * @param request The transaction details.
     * @return The created transaction.
     */
    @PostMapping("/transactions")
    public CreditCardTransactionResponse addTransaction(@Valid @RequestBody CreditCardTransactionRequest request) {
        CreateCreditCardTransactionCommand command = creditCardTransactionMapper.toCommand(request);
        return creditCardTransactionMapper.toResponse(creditCardTransactionService.addTransaction(command), SecurityUtil.getCurrentUserId());
    }
}
