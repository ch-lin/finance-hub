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
package ch.lin.finance.hub.backend.api.controller.accounting.reconciliation;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.ReconciliationService;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.command.LinkTransactionCommand;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.creditcard.dto.CreditCardTransactionResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.reconciliation.dto.AmazonOrderResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.reconciliation.dto.LinkTransactionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.reconciliation.dto.ReconciliationMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.reconciliation.dto.SuggestedMatchResponse;
import jakarta.validation.Valid;

/**
 * REST controller for handling reconciliation between different data sources.
 */
@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    /** Service that handles reconciliation logic. */
    private final ReconciliationService reconciliationService;
    private final ReconciliationMapper reconciliationMapper;

    /**
     * Constructs a new ReconciliationController.
     *
     * @param reconciliationService The service managing reconciliation logic.
     * @param reconciliationMapper  The mapper for reconciliation data.
     */
    public ReconciliationController(ReconciliationService reconciliationService,
            ReconciliationMapper reconciliationMapper) {
        this.reconciliationService = reconciliationService;
        this.reconciliationMapper = reconciliationMapper;
    }

    /**
     * Retrieves a paginated list of unmatched credit card transactions that are
     * pending reconciliation.
     *
     * @param pageable The pagination and sorting information (e.g.,
     *                 ?page=0&size=20).
     * @return A paginated list of unmatched credit card transactions.
     */
    @GetMapping("/unmatched")
    public ResponseEntity<Page<CreditCardTransactionResponse>> getUnmatchedTransactions(Pageable pageable) {
        Page<CreditCardTransactionResponse> transactions = reconciliationService
                .getUnmatchedAmazonTransactions(pageable)
                .map(result -> reconciliationMapper.toCreditCardTransactionResponse(result,
                        SecurityUtil.getCurrentUserId()));
        return ResponseEntity.ok(transactions);
    }

    /**
     * Retrieves a list of suggested shopping records (e.g., Amazon Orders) for
     * a given credit card transaction ID.
     *
     * @param transactionId The ID of the credit card transaction.
     * @return A list of suggested Amazon Orders with additional context flags.
     */
    @GetMapping("/{transactionId}/suggestions")
    public ResponseEntity<List<SuggestedMatchResponse>> getSuggestions(@PathVariable Long transactionId) {
        List<SuggestedMatchResponse> suggestions = reconciliationService.getSuggestions(transactionId).stream()
                .map(result -> reconciliationMapper.toSuggestedMatchResponse(result, SecurityUtil.getCurrentUserId()))
                .toList();
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Links a credit card transaction to a shopping order (e.g., Amazon Order).
     *
     * @param request The request body containing the IDs of the records to
     *                link.
     * @return An empty HTTP 200 OK response upon successful link creation.
     */
    @PostMapping("/link")
    public ResponseEntity<Void> linkTransaction(@RequestBody @Valid LinkTransactionRequest request) {
        LinkTransactionCommand command = reconciliationMapper.toCommand(request);
        reconciliationService.linkTransaction(command.getCreditCardTransactionId(), command.getAmazonOrderId());
        return ResponseEntity.ok().build();
    }

    /**
     * Searches for unmatched Amazon orders.
     *
     * @param orderId   Optional Amazon order ID.
     * @param minAmount Optional minimum amount.
     * @param maxAmount Optional maximum amount.
     * @return A list of matching Amazon orders.
     */
    @GetMapping("/amazon-orders/search")
    public ResponseEntity<List<AmazonOrderResponse>> searchUnmatchedAmazonOrders(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) java.math.BigDecimal minAmount,
            @RequestParam(required = false) java.math.BigDecimal maxAmount) {
        List<AmazonOrderResponse> orders = reconciliationService
                .searchUnmatchedAmazonOrders(orderId, minAmount, maxAmount).stream()
                .map(result -> reconciliationMapper.toAmazonOrderResponse(result, SecurityUtil.getCurrentUserId()))
                .toList();
        return ResponseEntity.ok(orders);
    }
}
