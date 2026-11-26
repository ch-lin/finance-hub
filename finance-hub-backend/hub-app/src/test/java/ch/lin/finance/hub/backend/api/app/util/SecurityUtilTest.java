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
package ch.lin.finance.hub.backend.api.app.util;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class SecurityUtilTest {

    private SecurityContext originalContext;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        originalContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    void getUserId_WithValidJwt_ReturnsSubject() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user123");

        String userId = SecurityUtil.getUserId(jwt);

        assertEquals("user123", userId);
    }

    @Test
    void getUserId_WithNullJwt_ThrowsUnauthorized() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> SecurityUtil.getUserId(null));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Valid JWT token is required", exception.getReason());
    }

    @Test
    void getUserId_WithNullSubject_ThrowsUnauthorized() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> SecurityUtil.getUserId(jwt));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Valid JWT token is required", exception.getReason());
    }

    @Test
    void getCurrentUserId_WithValidJwtAuthenticationToken_ReturnsSubject() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user456");
        JwtAuthenticationToken jwtAuth = mock(JwtAuthenticationToken.class);
        when(jwtAuth.getToken()).thenReturn(jwt);

        SecurityContextHolder.getContext().setAuthentication(jwtAuth);

        String userId = SecurityUtil.getCurrentUserId();

        assertEquals("user456", userId);
    }

    @Test
    void getCurrentUserId_WithNullAuthentication_ThrowsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> SecurityUtil.getCurrentUserId());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Valid JWT token is required", exception.getReason());
    }

    @Test
    void getCurrentUserId_WithNonJwtAuthentication_ThrowsUnauthorized() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> SecurityUtil.getCurrentUserId());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Valid JWT token is required", exception.getReason());
    }
}
