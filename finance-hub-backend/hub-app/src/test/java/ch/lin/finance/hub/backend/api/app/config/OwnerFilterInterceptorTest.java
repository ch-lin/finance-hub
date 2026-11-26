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
package ch.lin.finance.hub.backend.api.app.config;

import java.util.List;
import java.util.Objects;

import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import jakarta.persistence.EntityManager;

class OwnerFilterInterceptorTest {

    private OwnerFilterInterceptor interceptor;
    private EntityManager entityManager;
    private FamilyService familyService;
    private Session session;
    private Filter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        entityManager = mock(EntityManager.class);
        familyService = mock(FamilyService.class);
        session = mock(Session.class);
        filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(anyString())).thenReturn(filter);

        interceptor = new OwnerFilterInterceptor(entityManager, familyService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Mock SecurityContext
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "user123")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle_WithFamilyScope_ShouldEnableFamilyFilter() {
        request.addHeader("X-View-Scope", "FAMILY");
        List<String> memberIds = List.of("user123", "user456");
        when(familyService.getFamilyMemberIds("user123")).thenReturn(memberIds);

        boolean result = interceptor.preHandle(Objects.requireNonNull(request), Objects.requireNonNull(response),
                new Object());

        assertTrue(result);
        verify(familyService).getFamilyMemberIds("user123");
        verify(session).enableFilter("familyFilter");
        verify(filter).setParameter("currentUserId", "user123");
        verify(filter).setParameterList("familyUserIds", memberIds);
    }

    @Test
    void preHandle_WithFamilyScope_ButNoFamily_ShouldThrowForbidden() {
        request.addHeader("X-View-Scope", "FAMILY");
        when(familyService.getFamilyMemberIds("user123"))
                .thenThrow(new IllegalStateException("User does not belong to any family group."));

        org.springframework.web.server.ResponseStatusException exception = org.junit.jupiter.api.Assertions
                .assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> interceptor.preHandle(Objects.requireNonNull(request), Objects.requireNonNull(response),
                                new Object()));

        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
                exception.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals("User does not belong to a family group", exception.getReason());
    }

    @Test
    void preHandle_WithPersonalScope_ShouldEnablePersonalFilter() {
        request.addHeader("X-View-Scope", "PERSONAL");

        boolean result = interceptor.preHandle(Objects.requireNonNull(request), Objects.requireNonNull(response),
                new Object());

        assertTrue(result);
        verify(session).enableFilter("personalFilter");
        verify(filter).setParameter("userId", "user123");
    }

    @Test
    void preHandle_WithNoScope_ShouldEnablePersonalFilter() {
        boolean result = interceptor.preHandle(Objects.requireNonNull(request), Objects.requireNonNull(response),
                new Object());

        assertTrue(result);
        verify(session).enableFilter("personalFilter");
        verify(filter).setParameter("userId", "user123");
    }
}
