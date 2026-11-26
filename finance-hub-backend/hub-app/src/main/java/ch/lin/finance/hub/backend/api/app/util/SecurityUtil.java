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

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class SecurityUtil {

    private SecurityUtil() {
        // Utility class
    }

    /**
     * Extracts the user ID from a JWT token.
     * Throws an UNAUTHORIZED exception if the token or its subject is missing.
     *
     * @param jwt The JWT token from the security context
     * @return The user ID (subject)
     * @throws ResponseStatusException if jwt or subject is null
     */
    public static String getUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid JWT token is required");
        }
        return jwt.getSubject();
    }

    /**
     * Gets the current user ID from the SecurityContext.
     * Throws UNAUTHORIZED if not authenticated.
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return getUserId(jwtAuth.getToken());
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid JWT token is required");
    }
}
