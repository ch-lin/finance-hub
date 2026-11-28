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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.reconciliation.TransactionLinkRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.controller.security.AbstractMultiUserIsolationIntegrationTest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.reconciliation.TransactionLink;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

class ReconciliationIsolationIntegrationTest extends AbstractMultiUserIsolationIntegrationTest {

    @Autowired
    private TransactionLinkRepository transactionLinkRepository;

    @Autowired
    private CreditCardTransactionRepository creditCardTransactionRepository;

    @Autowired
    private AmazonOrderRepository amazonOrderRepository;

    @Override
    protected void cleanUpModuleData() {
        transactionLinkRepository.deleteAll();
        creditCardTransactionRepository.deleteAll();
        amazonOrderRepository.deleteAll();
    }

    @Test
    void reconciliation_shouldEnforceOwnerIsolationAndReturn404WithoutLeakingLinkState() throws Exception {
        LocalDate txDate = LocalDate.of(2026, 2, 10);

        setAuthenticatedUser(USER_A);
        CreditCardTransaction txA = creditCardTransactionRepository.save(
                new CreditCardTransaction("Card-A", txDate, "AMAZON.CO.JP", 3500));
        AmazonOrder orderA = new AmazonOrder("AMZ-A-1", txDate.minusDays(2));
        orderA.setTotalAmount(new BigDecimal("3500.00"));
        orderA.setBilledAmount(new BigDecimal("3500.00"));
        orderA = amazonOrderRepository.save(orderA);

        setAuthenticatedUser(USER_B);
        CreditCardTransaction txBUnlinked = creditCardTransactionRepository.save(
                new CreditCardTransaction("Card-B", txDate, "AMAZON.CO.JP", 4500));
        AmazonOrder orderBUnlinked = new AmazonOrder("AMZ-B-1", txDate.minusDays(2));
        orderBUnlinked.setTotalAmount(new BigDecimal("4500.00"));
        orderBUnlinked.setBilledAmount(new BigDecimal("4500.00"));
        orderBUnlinked = amazonOrderRepository.save(orderBUnlinked);

        CreditCardTransaction txBLinked = creditCardTransactionRepository.save(
                new CreditCardTransaction("Card-B", txDate, "AMAZON.CO.JP", 5500));
        AmazonOrder orderBLinked = new AmazonOrder("AMZ-B-LINKED", txDate.minusDays(1));
        orderBLinked.setTotalAmount(new BigDecimal("5500.00"));
        orderBLinked.setBilledAmount(new BigDecimal("5500.00"));
        orderBLinked = amazonOrderRepository.save(orderBLinked);
        transactionLinkRepository.save(new TransactionLink(txBLinked, orderBLinked));

        SecurityContextHolder.clearContext();

        // Unmatched lists stay locked to User A even under FAMILY header
        mockMvc.perform(get("/reconciliation/unmatched")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(txA.getId()));

        mockMvc.perform(get("/reconciliation/amazon-orders/search")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value("AMZ-A-1"));

        // Accessing User B's transaction suggestions returns 404
        mockMvc.perform(get("/reconciliation/{id}/suggestions", txBUnlinked.getId())
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());

        // Linking with User B's unlinked or already-linked IDs must always return 404
        // (never leaking "already linked")
        String linkOtherBoth = """
                {"creditCardTransactionId": %d, "amazonOrderId": %d}
                """.formatted(txBUnlinked.getId(), orderBUnlinked.getId());
        mockMvc.perform(post("/reconciliation/link")
                .header("Authorization", "Bearer " + TOKEN_A)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(linkOtherBoth)))
                .andExpect(status().isNotFound());

        String linkOtherAlreadyLinkedTx = """
                {"creditCardTransactionId": %d, "amazonOrderId": %d}
                """.formatted(txBLinked.getId(), orderA.getId());
        mockMvc.perform(post("/reconciliation/link")
                .header("Authorization", "Bearer " + TOKEN_A)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(linkOtherAlreadyLinkedTx)))
                .andExpect(status().isNotFound());

        String linkOtherAlreadyLinkedOrder = """
                {"creditCardTransactionId": %d, "amazonOrderId": %d}
                """.formatted(txA.getId(), orderBLinked.getId());
        mockMvc.perform(post("/reconciliation/link")
                .header("Authorization", "Bearer " + TOKEN_A)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(linkOtherAlreadyLinkedOrder)))
                .andExpect(status().isNotFound());

        // User A's own suggestions succeed and match only User A's order
        mockMvc.perform(get("/reconciliation/{id}/suggestions", txA.getId())
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].order.orderId").value("AMZ-A-1"));
    }
}
