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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonOrderCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonOrderResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unused", "null" })
class AmazonServiceImplTest {

    private static final String USER_ID = "user-1";

    @Mock
    private AmazonOrderRepository amazonOrderRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AmazonServiceImpl service;
    private SecurityContext previousSecurityContext;

    @BeforeEach
    void setUp() {
        previousSecurityContext = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", USER_ID).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);

        service = new AmazonServiceImpl(amazonOrderRepository, transactionTemplate);

        // Mock transaction template to just execute the callback
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
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
    void testCleanup() {
        service.cleanup();
        verify(amazonOrderRepository).cleanTable();
        verify(amazonOrderRepository).resetSequence();
    }

    @Test
    void testSaveOrder_NewOrder() {
        SaveAmazonOrderCommand command = new SaveAmazonOrderCommand("123", null, null, null, null, null, null, null,
                null, null, null, null, null);
        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(any(AmazonOrder.class))).thenReturn(order);

        AmazonOrderResult result = service.saveOrder(command);

        assertEquals("123", result.getOrderId());
    }

    @Test
    void testSaveOrder_ExistingOrder() {
        SaveAmazonOrderCommand command = new SaveAmazonOrderCommand("123", null, null, null, null, null, null, null,
                null, null, null, null, null);
        AmazonOrder existingOrder = new AmazonOrder("123", null);
        when(amazonOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "123")).thenReturn(Optional.of(existingOrder));
        when(amazonOrderRepository.save(existingOrder)).thenReturn(existingOrder);

        AmazonOrderResult result = service.saveOrder(command);

        assertEquals("123", result.getOrderId());
    }

    @Test
    void testGetOrder() {
        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "123")).thenReturn(Optional.of(order));

        Optional<AmazonOrderResult> result = service.getOrder("123");

        assertTrue(result.isPresent());
        assertEquals("123", result.get().getOrderId());
        verify(amazonOrderRepository).findByOwnerUserIdAndOrderId(USER_ID, "123");
    }

    @Test
    void testGetOrder_NotFound() {
        when(amazonOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "999")).thenReturn(Optional.empty());

        Optional<AmazonOrderResult> result = service.getOrder("999");

        assertFalse(result.isPresent());
        verify(amazonOrderRepository).findByOwnerUserIdAndOrderId(USER_ID, "999");
    }

    @Test
    void testGetOrderWithDetails_Success() {
        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderRepository.findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "123"))
                .thenReturn(Optional.of(order));

        Optional<AmazonOrderResult> result = service.getOrderWithDetails("123");

        assertTrue(result.isPresent());
        assertEquals("123", result.get().getOrderId());
        verify(amazonOrderRepository).findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "123");
    }

    @Test
    void testGetOrderWithDetails_NotFound() {
        when(amazonOrderRepository.findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "999")).thenReturn(Optional.empty());

        Optional<AmazonOrderResult> result = service.getOrderWithDetails("999");

        assertFalse(result.isPresent());
        verify(amazonOrderRepository).findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "999");
    }
}
