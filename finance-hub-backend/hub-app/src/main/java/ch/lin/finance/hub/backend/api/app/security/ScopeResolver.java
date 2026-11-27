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
package ch.lin.finance.hub.backend.api.app.security;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the effective data-visibility scope (personal vs. family) from the
 * current HTTP request's {@code X-View-Scope} header. Defaults to personal
 * scope outside HTTP request contexts (e.g. background jobs).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScopeResolver {

    public static final String HEADER_VIEW_SCOPE = "X-View-Scope";
    public static final String SCOPE_FAMILY = "FAMILY";

    private final FamilyService familyService;

    /**
     * Represents the resolved visibility scope for read queries.
     *
     * @param currentUserId   the authenticated user's ID
     * @param isFamily        whether family scope is active
     * @param familyMemberIds the user IDs in the family group (empty when personal)
     */
    public record ReadableScope(String currentUserId, boolean isFamily, List<String> familyMemberIds) {

        public static ReadableScope personal(String userId) {
            return new ReadableScope(userId, false, List.of());
        }

        public static ReadableScope family(String userId, List<String> memberIds) {
            return new ReadableScope(userId, true, List.copyOf(memberIds));
        }
    }

    /**
     * Resolves the {@link ReadableScope} for the current authenticated user.
     *
     * @return the resolved {@link ReadableScope}
     */
    public ReadableScope resolveReadableScope() {
        String userId = SecurityUtil.getCurrentUserId();
        String viewScope = extractViewScopeHeader();

        if (SCOPE_FAMILY.equalsIgnoreCase(viewScope)) {
            try {
                List<String> memberIds = familyService.getFamilyMemberIds(userId);
                return ReadableScope.family(userId, memberIds);
            } catch (IllegalStateException e) {
                log.warn("Security Alert: User {} requested FAMILY scope but does not belong to a family group",
                        userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not belong to a family group");
            }
        }

        return ReadableScope.personal(userId);
    }

    private String extractViewScopeHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader(HEADER_VIEW_SCOPE);
        }
        return null;
    }
}
