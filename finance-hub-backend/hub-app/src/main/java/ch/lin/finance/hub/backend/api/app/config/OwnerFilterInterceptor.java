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

import org.hibernate.Filter;
import org.hibernate.Session;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Interceptor that automatically enables the correct Hibernate filter on the
 * current Session based on the X-View-Scope header. By default, it enables the
 * "personalFilter". If X-View-Scope: FAMILY is set, it enables the
 * "familyFilter" with the user's family members.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OwnerFilterInterceptor implements HandlerInterceptor {

    private final EntityManager entityManager;
    private final FamilyService familyService;

    private static final String HEADER_VIEW_SCOPE = "X-View-Scope";
    private static final String SCOPE_FAMILY = "FAMILY";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        String userId = null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            userId = jwtAuth.getToken().getSubject();
        }

        if (userId != null) {
            // Unwrap the JPA EntityManager to get the native Hibernate Session
            Session session = entityManager.unwrap(Session.class);
            String viewScope = request.getHeader(HEADER_VIEW_SCOPE);

            if (SCOPE_FAMILY.equalsIgnoreCase(viewScope)) {
                try {
                    List<String> memberIds = familyService.getFamilyMemberIds(userId);
                    // The parameters for familyFilter are 'currentUserId' and 'familyUserIds' as defined in
                    // MultiUserBaseEntity
                    Filter familyFilter = session.enableFilter("familyFilter");
                    familyFilter.setParameter("currentUserId", userId);
                    familyFilter.setParameterList("familyUserIds", memberIds);
                    log.trace("Enabled Hibernate familyFilter for userId: {} with memberIds: {}", userId, memberIds);
                } catch (IllegalStateException e) {
                    log.warn("Security Alert: User {} requested FAMILY scope but does not belong to a family group",
                            userId);
                    throw new ResponseStatusException(FORBIDDEN, "User does not belong to a family group");
                }
            } else {
                // Enable the default personalFilter
                session.enableFilter("personalFilter").setParameter("userId", userId);
                log.trace("Enabled Hibernate personalFilter for userId: {}", userId);
            }
        }

        return true;
    }
}
