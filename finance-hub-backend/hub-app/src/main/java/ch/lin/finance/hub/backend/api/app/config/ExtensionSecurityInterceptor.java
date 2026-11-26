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

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Interceptor dedicated to protecting Extension APIs from malicious usage.
 * It fails-fast if a request attempts to use the FAMILY view scope on routes
 * that should only access personal data.
 */
@Component
@Slf4j
public class ExtensionSecurityInterceptor implements HandlerInterceptor {

    private static final String HEADER_VIEW_SCOPE = "X-View-Scope";
    private static final String SCOPE_FAMILY = "FAMILY";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        String viewScope = request.getHeader(HEADER_VIEW_SCOPE);

        if (SCOPE_FAMILY.equalsIgnoreCase(viewScope)) {
            log.warn("Security Alert: Attempted to access strictly personal API with FAMILY scope on URI: {}",
                    request.getRequestURI());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Family scope is not allowed for strictly personal APIs");
        }

        return true;
    }
}
