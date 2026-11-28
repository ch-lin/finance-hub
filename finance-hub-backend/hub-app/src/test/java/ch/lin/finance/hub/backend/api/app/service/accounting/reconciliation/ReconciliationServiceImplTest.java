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
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation.TransactionLinkRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.ReconciliationAmazonOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.result.SuggestedMatchResult;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation.TransactionLink;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "null", "unused" })
class ReconciliationServiceImplTest {

    private static final String USER_ID = "user-1";

    @Mock
    private CreditCardTransactionRepository creditCardRepository;

    @Mock
    private AmazonOrderRepository amazonOrderRepository;

    @Mock
    private TransactionLinkRepository transactionLinkRepository;

    @Mock
    private OwnershipGuard ownershipGuard;

    @InjectMocks
    private ReconciliationServiceImpl reconciliationService;

    private SecurityContext previousSecurityContext;
    private CreditCardTransaction transaction;
    private AmazonOrder order1;
    private AmazonOrder order2;
    private LocalDate transactionDate;

    @BeforeEach
    void setUp() {
        previousSecurityContext = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", USER_ID).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);

        transactionDate = LocalDate.of(2025, 6, 15);
        transaction = new CreditCardTransaction("TestCard", transactionDate, "TestStore", -100);
        ReflectionTestUtils.setField(transaction, "id", 1L);
        transaction.setOwnerUserId(USER_ID);

        order1 = new AmazonOrder("order-1", transactionDate);
        ReflectionTestUtils.setField(order1, "id", 10L);
        order1.setOwnerUserId(USER_ID);
        order1.setBilledAmount(new BigDecimal("100.0"));

        order2 = new AmazonOrder("order-2", transactionDate);
        ReflectionTestUtils.setField(order2, "id", 20L);
        order2.setOwnerUserId(USER_ID);
        order2.setBilledAmount(new BigDecimal("100.0"));
    }

    @AfterEach
    void tearDown() {
        if (previousSecurityContext != null) {
            SecurityContextHolder.setContext(previousSecurityContext);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void testGetUnmatchedAmazonTransactions() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<CreditCardTransaction> page = new PageImpl<>(List.of(transaction));
        when(creditCardRepository.findUnmatchedAmazonTransactionsByOwnerUserId(USER_ID, pageable)).thenReturn(page);

        Page<CreditCardTransactionResult> result = reconciliationService.getUnmatchedAmazonTransactions(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(creditCardRepository).findUnmatchedAmazonTransactionsByOwnerUserId(USER_ID, pageable);
    }

    @Test
    void testGetSuggestions_TransactionNotFound() {
        when(ownershipGuard.loadOwned(creditCardRepository, 1L))
                .thenThrow(new ItemNotFoundException("Resource not found with id: 1"));

        ItemNotFoundException exception = assertThrows(ItemNotFoundException.class, () -> {
            reconciliationService.getSuggestions(1L);
        });
        assertTrue(exception.getMessage().contains("Resource not found with id: 1"));
    }

    @Test
    void testGetSuggestions_Success() {
        when(ownershipGuard.loadOwned(creditCardRepository, 1L)).thenReturn(transaction);
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(List.of(order1));
        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(5));

        List<SuggestedMatchResult> suggestions = reconciliationService.getSuggestions(1L);

        assertEquals(1, suggestions.size());
        SuggestedMatchResult dto = suggestions.get(0);
        assertEquals(order1.getId(), dto.getOrder().getId());
        assertFalse(dto.isPreOrder());
        assertFalse(dto.isHasMultipleMatches());
    }

    @Test
    void testFindFuzzyMatches_NormalMatch_Single() {
        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(5));
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(List.of(order1));

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertEquals(1, matches.size());
        assertEquals(order1.getId(), matches.get(0).getOrder().getId());
        assertFalse(matches.get(0).isPreOrder());
        assertFalse(matches.get(0).isHasMultipleMatches());
    }

    @Test
    void testFindFuzzyMatches_NormalMatch_MultipleWithSorting() {
        // order1 is closer by shipping date
        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(10));
        order1.setShippingDate(transactionDate.minusDays(2));

        // order2 is closer by order date, but shipping date takes precedence if
        // available
        ReflectionTestUtils.setField(order2, "orderDate", transactionDate.minusDays(1));
        order2.setShippingDate(transactionDate.minusDays(5));

        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(List.of(order1, order2));

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertEquals(2, matches.size());
        // order1 should be first because shipping date difference is 2 days (order2 is
        // 5 days)
        assertEquals(order1.getId(), matches.get(0).getOrder().getId());
        assertEquals(order2.getId(), matches.get(1).getOrder().getId());

        assertTrue(matches.get(0).isHasMultipleMatches());
        assertTrue(matches.get(1).isHasMultipleMatches());
    }

    @Test
    void testFindFuzzyMatches_PreOrderMatch() {
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(Collections.emptyList());

        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(30)); // In pre-order window
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusMonths(24),
                transactionDate.minusDays(15)))
                .thenReturn(List.of(order1));

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertEquals(1, matches.size());
        assertEquals(order1.getId(), matches.get(0).getOrder().getId());
        assertTrue(matches.get(0).isPreOrder());
        assertFalse(matches.get(0).isHasMultipleMatches());
    }

    @Test
    void testFindFuzzyMatches_NoMatch() {
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(Collections.emptyList());
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusMonths(24),
                transactionDate.minusDays(15)))
                .thenReturn(Collections.emptyList());

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertTrue(matches.isEmpty());
    }

    @Test
    void testFindFuzzyMatches_NormalMatch_AlreadyLinkedOrderExcluded() {
        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(5));
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(List.of(order1));
        TransactionLink link = new TransactionLink(transaction, order1);
        when(transactionLinkRepository.findByAmazonOrderId(10L)).thenReturn(List.of(link));
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusMonths(24),
                transactionDate.minusDays(15)))
                .thenReturn(Collections.emptyList());

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertTrue(matches.isEmpty());
    }

    @Test
    void testLinkTransaction_AlreadyLinked() {
        when(ownershipGuard.loadOwned(creditCardRepository, 1L)).thenReturn(transaction);
        when(ownershipGuard.loadOwned(amazonOrderRepository, 10L)).thenReturn(order1);
        TransactionLink link = new TransactionLink(transaction, order1);
        when(transactionLinkRepository.findByCreditCardTransactionId(1L))
                .thenReturn(List.of(link));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            reconciliationService.linkTransaction(1L, 10L);
        });
        assertTrue(exception.getMessage().contains("is already linked"));
    }

    @Test
    void testLinkTransaction_AmazonOrderAlreadyLinked() {
        when(ownershipGuard.loadOwned(creditCardRepository, 1L)).thenReturn(transaction);
        when(ownershipGuard.loadOwned(amazonOrderRepository, 10L)).thenReturn(order1);
        when(transactionLinkRepository.findByCreditCardTransactionId(1L)).thenReturn(Collections.emptyList());
        TransactionLink link = new TransactionLink(transaction, order1);
        when(transactionLinkRepository.findByAmazonOrderId(10L)).thenReturn(List.of(link));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            reconciliationService.linkTransaction(1L, 10L);
        });
        assertTrue(exception.getMessage().contains("Amazon order with ID 10 is already linked"));
    }

    @Test
    void testLinkTransaction_CreditCardNotFound() {
        when(ownershipGuard.loadOwned(creditCardRepository, 1L))
                .thenThrow(new ItemNotFoundException("Resource not found with id: 1"));

        ItemNotFoundException exception = assertThrows(ItemNotFoundException.class, () -> {
            reconciliationService.linkTransaction(1L, 10L);
        });
        assertTrue(exception.getMessage().contains("Resource not found with id: 1"));
    }

    @Test
    void testLinkTransaction_AmazonOrderNotFound() {
        when(ownershipGuard.loadOwned(creditCardRepository, 1L)).thenReturn(transaction);
        when(ownershipGuard.loadOwned(amazonOrderRepository, 10L))
                .thenThrow(new ItemNotFoundException("Resource not found with id: 10"));

        ItemNotFoundException exception = assertThrows(ItemNotFoundException.class, () -> {
            reconciliationService.linkTransaction(1L, 10L);
        });
        assertTrue(exception.getMessage().contains("Resource not found with id: 10"));
    }

    @Test
    void testLinkTransaction_Success() {
        when(ownershipGuard.loadOwned(creditCardRepository, 1L)).thenReturn(transaction);
        when(ownershipGuard.loadOwned(amazonOrderRepository, 10L)).thenReturn(order1);
        when(transactionLinkRepository.findByCreditCardTransactionId(1L)).thenReturn(Collections.emptyList());

        reconciliationService.linkTransaction(1L, 10L);

        verify(transactionLinkRepository).save(any(TransactionLink.class));
    }

    @Test
    void testSearchUnmatchedAmazonOrders() {
        when(amazonOrderRepository.searchUnmatchedOrdersByOwnerUserId(USER_ID, "order", BigDecimal.TEN,
                BigDecimal.valueOf(500)))
                .thenReturn(List.of(order1));

        List<ReconciliationAmazonOrderResult> result = reconciliationService.searchUnmatchedAmazonOrders(
                "order", BigDecimal.TEN, BigDecimal.valueOf(500));

        assertEquals(1, result.size());
        assertEquals(order1.getId(), result.get(0).getId());
        verify(amazonOrderRepository).searchUnmatchedOrdersByOwnerUserId(USER_ID, "order", BigDecimal.TEN,
                BigDecimal.valueOf(500));
    }

    @Test
    void testGetSuggestions_NullId() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            reconciliationService.getSuggestions(null);
        });
        assertNotNull(exception);
    }

    @Test
    void testLinkTransaction_NullCreditCardId() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            reconciliationService.linkTransaction(null, 10L);
        });
        assertNotNull(exception);
    }

    @Test
    void testLinkTransaction_NullAmazonOrderId() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            reconciliationService.linkTransaction(1L, null);
        });
        assertNotNull(exception);
    }

    @Test
    void testFindFuzzyMatches_NormalMatch_AmountMismatch() {
        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(5));
        order1.setBilledAmount(new BigDecimal("50.0")); // Different amount

        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(List.of(order1));

        // In this case, phase 1 finds nothing. Phase 2 also finds nothing.
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusMonths(24),
                transactionDate.minusDays(15)))
                .thenReturn(Collections.emptyList());

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertTrue(matches.isEmpty());
    }

    @Test
    void testFindFuzzyMatches_PreOrderMatch_AmountMismatch() {
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(Collections.emptyList());

        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(30)); // In pre-order window
        order1.setBilledAmount(new BigDecimal("50.0")); // Different amount

        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusMonths(24),
                transactionDate.minusDays(15)))
                .thenReturn(List.of(order1));

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertTrue(matches.isEmpty());
    }

    @Test
    void testFindFuzzyMatches_PreOrderMatch_Multiple() {
        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(Collections.emptyList());

        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(30));
        ReflectionTestUtils.setField(order2, "orderDate", transactionDate.minusDays(40));

        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusMonths(24),
                transactionDate.minusDays(15)))
                .thenReturn(List.of(order1, order2));

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertEquals(2, matches.size());
        assertTrue(matches.get(0).isPreOrder());
        assertTrue(matches.get(0).isHasMultipleMatches());
        assertTrue(matches.get(1).isPreOrder());
        assertTrue(matches.get(1).isHasMultipleMatches());
    }

    @Test
    void testFindFuzzyMatches_NormalMatch_NullShippingDateFallback() {
        // order1 has no shipping date, should fallback to orderDate
        ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(3));
        order1.setShippingDate(null);

        // order2 has shipping date
        ReflectionTestUtils.setField(order2, "orderDate", transactionDate.minusDays(10));
        order2.setShippingDate(transactionDate.minusDays(5));

        when(amazonOrderRepository.findByOwnerUserIdAndOrderDateBetween(USER_ID, transactionDate.minusDays(14),
                transactionDate))
                .thenReturn(List.of(order1, order2));

        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);

        assertEquals(2, matches.size());
        // order1 should be first because orderDate difference is 3 days (order2
        // shipping date is 5 days)
        assertEquals(order1.getId(), matches.get(0).getOrder().getId());
        assertEquals(order2.getId(), matches.get(1).getOrder().getId());
    }
}
