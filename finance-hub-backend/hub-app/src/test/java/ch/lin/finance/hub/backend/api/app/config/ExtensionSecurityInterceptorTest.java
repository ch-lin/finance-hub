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

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

class ExtensionSecurityInterceptorTest {

    private ExtensionSecurityInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        interceptor = new ExtensionSecurityInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI("/amazon/extension/sync");
    }

    @Test
    void preHandle_WithFamilyScope_ShouldThrowForbidden() {
        request.addHeader("X-View-Scope", "FAMILY");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interceptor.preHandle(Objects.requireNonNull(request), Objects.requireNonNull(response),
                        new Object()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Family scope is not allowed for strictly personal APIs", exception.getReason());
    }

    @Test
    void preHandle_WithPersonalScope_ShouldPass() {
        request.addHeader("X-View-Scope", "PERSONAL");

        boolean result = interceptor.preHandle(Objects.requireNonNull(request), Objects.requireNonNull(response),
                new Object());

        assertTrue(result);
    }

    @Test
    void preHandle_WithNoScope_ShouldPass() {
        boolean result = interceptor.preHandle(Objects.requireNonNull(request), Objects.requireNonNull(response),
                new Object());

        assertTrue(result);
    }
}
