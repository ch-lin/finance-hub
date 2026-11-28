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
package ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation.TransactionLinkRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.ReconciliationAmazonOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.SuggestedMatchResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation.TransactionLink;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import lombok.NonNull;

/**
 * Core implementation of {@link ReconciliationService}.
 * <p>
 * Handles the logic for fuzzy matching credit card transactions to Amazon
 * orders
 * based on date proximity and exact un-reconciled amount matches.
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    private final CreditCardTransactionRepository creditCardRepository;
    private final AmazonOrderRepository amazonOrderRepository;
    private final TransactionLinkRepository transactionLinkRepository;
    private final OwnershipGuard ownershipGuard;

    /**
     * Constructs the service with required repositories and authorization guard.
     *
     * @param creditCardRepository      Repository for credit card transactions.
     * @param amazonOrderRepository     Repository for Amazon orders.
     * @param transactionLinkRepository Repository for storing successful links.
     * @param ownershipGuard            Guard enforcing ownership verification.
     */
    public ReconciliationServiceImpl(CreditCardTransactionRepository creditCardRepository,
            AmazonOrderRepository amazonOrderRepository,
            TransactionLinkRepository transactionLinkRepository,
            OwnershipGuard ownershipGuard) {
        this.creditCardRepository = creditCardRepository;
        this.amazonOrderRepository = amazonOrderRepository;
        this.transactionLinkRepository = transactionLinkRepository;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CreditCardTransactionResult> getUnmatchedAmazonTransactions(Pageable pageable) {
        return creditCardRepository
                .findUnmatchedAmazonTransactionsByOwnerUserId(SecurityUtil.getCurrentUserId(), pageable)
                .map(CreditCardTransactionResult::fromEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<SuggestedMatchResult> getSuggestions(@NonNull Long transactionId) {
        CreditCardTransaction transaction = ownershipGuard.loadOwned(creditCardRepository, transactionId);
        return findFuzzyMatches(transaction);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<SuggestedMatchResult> findFuzzyMatches(CreditCardTransaction transaction) {
        String ownerUserId = Objects.requireNonNull(transaction.getOwnerUserId(),
                "Transaction ownerUserId must not be null");
        // Using absolute value assuming credit card expenses might be recorded as
        // negatives
        BigDecimal targetAmount = BigDecimal.valueOf(Math.abs(transaction.getAmount()));
        LocalDate maxDate = transaction.getTransactionDate();

        // Phase 1: Check the normal shipping window (e.g., 14 days before the
        // transaction)
        LocalDate normalMinDate = maxDate.minusDays(14);
        List<AmazonOrder> normalMatches = amazonOrderRepository
                .findByOwnerUserIdAndOrderDateBetween(ownerUserId, normalMinDate, maxDate).stream()
                .filter(order -> order.getUnreconciledBalance().compareTo(targetAmount) == 0)
                .filter(order -> transactionLinkRepository.findByAmazonOrderId(order.getId()).isEmpty())
                .collect(Collectors.toList());

        if (!normalMatches.isEmpty()) {
            boolean hasMultiple = normalMatches.size() > 1;
            return sortMatchesByProximity(normalMatches, maxDate).stream()
                    .map(order -> SuggestedMatchResult.fromEntity(order, false, hasMultiple))
                    .collect(Collectors.toList());
        }

        // Phase 2: If no matches found, assume it's a pre-order.
        // Search an extended window without overlapping the previously searched dates.
        LocalDate preOrderMinDate = maxDate.minusMonths(24);
        List<AmazonOrder> preOrderMatches = amazonOrderRepository
                .findByOwnerUserIdAndOrderDateBetween(ownerUserId, preOrderMinDate, normalMinDate.minusDays(1))
                .stream()
                .filter(order -> order.getUnreconciledBalance().compareTo(targetAmount) == 0)
                .filter(order -> transactionLinkRepository.findByAmazonOrderId(order.getId()).isEmpty())
                .collect(Collectors.toList());

        List<AmazonOrder> sortedPreOrderMatches = sortMatchesByProximity(preOrderMatches, maxDate);

        if (!sortedPreOrderMatches.isEmpty()) {
            logger.info("Found {} pre-order fallback match(es) for credit card transaction ID: {}",
                    sortedPreOrderMatches.size(), transaction.getId());
        }

        boolean hasMultiplePreOrder = sortedPreOrderMatches.size() > 1;
        return sortedPreOrderMatches.stream()
                .map(order -> SuggestedMatchResult.fromEntity(order, true, hasMultiplePreOrder))
                .collect(Collectors.toList());
    }

    /**
     * Sorts a list of Amazon orders based on their temporal proximity to a target
     * date.
     * <p>
     * It prefers shipping date if available; otherwise, it falls back to the order
     * date.
     *
     * @param matches    The list of candidate Amazon orders.
     * @param targetDate The transaction date to compare against.
     * @return A sorted list of orders, closest dates first.
     */
    private List<AmazonOrder> sortMatchesByProximity(List<AmazonOrder> matches, LocalDate targetDate) {
        return matches.stream()
                .sorted((o1, o2) -> {
                    LocalDate date1 = o1.getShippingDate() != null ? o1.getShippingDate() : o1.getOrderDate();
                    LocalDate date2 = o2.getShippingDate() != null ? o2.getShippingDate() : o2.getOrderDate();
                    long daysBetween1 = Math.abs(ChronoUnit.DAYS.between(date1, targetDate));
                    long daysBetween2 = Math.abs(ChronoUnit.DAYS.between(date2, targetDate));
                    return Long.compare(daysBetween1, daysBetween2);
                })
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void linkTransaction(@NonNull Long creditCardTransactionId, @NonNull Long amazonOrderId) {
        CreditCardTransaction transaction = ownershipGuard.loadOwned(creditCardRepository, creditCardTransactionId);
        AmazonOrder order = ownershipGuard.loadOwned(amazonOrderRepository, amazonOrderId);

        // Prevent duplicate linking
        if (!transactionLinkRepository.findByCreditCardTransactionId(creditCardTransactionId).isEmpty()) {
            throw new IllegalStateException(
                    "Credit card transaction with ID " + creditCardTransactionId + " is already linked.");
        }
        if (!transactionLinkRepository.findByAmazonOrderId(amazonOrderId).isEmpty()) {
            throw new IllegalStateException(
                    "Amazon order with ID " + amazonOrderId + " is already linked.");
        }

        TransactionLink link = new TransactionLink(transaction, order);
        transactionLinkRepository.save(link);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationAmazonOrderResult> searchUnmatchedAmazonOrders(String orderId, BigDecimal minAmount,
            BigDecimal maxAmount) {
        return amazonOrderRepository
                .searchUnmatchedOrdersByOwnerUserId(SecurityUtil.getCurrentUserId(), orderId, minAmount, maxAmount)
                .stream().map(ReconciliationAmazonOrderResult::fromEntity).collect(Collectors.toList());
    }
}
