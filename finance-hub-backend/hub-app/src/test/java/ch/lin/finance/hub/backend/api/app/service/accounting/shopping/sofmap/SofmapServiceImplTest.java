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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.SofmapOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapOrderResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SofmapServiceImplTest {

    private static final String USER_ID = "user-1";

    @Mock
    private SofmapOrderRepository sofmapOrderRepository;

    private SofmapServiceImpl service;
    private SecurityContext previousSecurityContext;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        previousSecurityContext = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", USER_ID).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);

        service = new SofmapServiceImpl(sofmapOrderRepository);
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        if (previousSecurityContext != null) {
            SecurityContextHolder.setContext(previousSecurityContext);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void testSaveOrder_NewOrder() {
        SofmapOrder order = new SofmapOrder("123", null);
        when(sofmapOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "123")).thenReturn(Optional.empty());
        when(sofmapOrderRepository.save(order)).thenReturn(order);
        when(sofmapOrderRepository.findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "123"))
                .thenReturn(Optional.of(order));

        SofmapOrderResult result = service.saveOrder(order);

        assertNotNull(result);
        assertEquals("123", result.getOrderId());
        verify(sofmapOrderRepository).save(order);
    }

    @Test
    void testSaveOrder_ExistingOrder() {
        SofmapOrder newOrder = new SofmapOrder("123", null);
        SofmapOrder existingOrder = new SofmapOrder("123", null);
        when(sofmapOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "123"))
                .thenReturn(Optional.of(existingOrder));
        when(sofmapOrderRepository.save(existingOrder)).thenReturn(existingOrder);
        when(sofmapOrderRepository.findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "123"))
                .thenReturn(Optional.of(existingOrder));

        SofmapOrderResult result = service.saveOrder(newOrder);

        assertNotNull(result);
        assertEquals("123", result.getOrderId());
        verify(sofmapOrderRepository).save(existingOrder);
    }

    @Test
    void testGetOrder() {
        SofmapOrder order = new SofmapOrder("123", null);
        when(sofmapOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "123")).thenReturn(Optional.of(order));

        Optional<SofmapOrderResult> result = service.getOrder("123");

        assertTrue(result.isPresent());
        assertEquals("123", result.get().getOrderId());
        verify(sofmapOrderRepository).findByOwnerUserIdAndOrderId(USER_ID, "123");
    }

    @Test
    void testGetOrder_NotFound() {
        when(sofmapOrderRepository.findByOwnerUserIdAndOrderId(USER_ID, "999")).thenReturn(Optional.empty());

        Optional<SofmapOrderResult> result = service.getOrder("999");

        assertFalse(result.isPresent());
        verify(sofmapOrderRepository).findByOwnerUserIdAndOrderId(USER_ID, "999");
    }

    @Test
    void testGetOrderWithDetails_Success() {
        SofmapOrder order = new SofmapOrder("123", null);
        when(sofmapOrderRepository.findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "123"))
                .thenReturn(Optional.of(order));

        Optional<SofmapOrderResult> result = service.getOrderWithDetails("123");

        assertTrue(result.isPresent());
        assertEquals("123", result.get().getOrderId());
        verify(sofmapOrderRepository).findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "123");
    }

    @Test
    void testGetOrderWithDetails_NotFound() {
        when(sofmapOrderRepository.findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "999"))
                .thenReturn(Optional.empty());

        Optional<SofmapOrderResult> result = service.getOrderWithDetails("999");

        assertFalse(result.isPresent());
        verify(sofmapOrderRepository).findWithDetailsByOwnerUserIdAndOrderId(USER_ID, "999");
    }
}
