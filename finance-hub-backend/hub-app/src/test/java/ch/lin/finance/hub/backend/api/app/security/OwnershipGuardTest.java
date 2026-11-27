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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.security.ScopeResolver.ReadableScope;
import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;

@ExtendWith(MockitoExtension.class)
class OwnershipGuardTest {

    @Mock
    private FamilyService familyService;

    @Mock
    private JpaRepository<ChildProfile, Long> repository;

    private ScopeResolver scopeResolver;
    private OwnershipGuard ownershipGuard;

    @BeforeEach
    void setUp() {
        scopeResolver = new ScopeResolver(familyService);
        ownershipGuard = new OwnershipGuard(familyService, scopeResolver);
        setAuthenticatedUser("user-a");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void setAuthenticatedUser(String userId) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", userId));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private ChildProfile recordOwnedBy(String ownerUserId, PrivacyLevel privacyLevel) {
        return ChildProfile.builder()
                .name("Child")
                .birthDate(java.time.LocalDate.of(2020, 1, 1))
                .ownerUserId(ownerUserId)
                .privacyLevel(privacyLevel)
                .build();
    }

    @Test
    void requireOwner_shouldPassForOwner() {
        ownershipGuard.requireOwner(recordOwnedBy("user-a", PrivacyLevel.PRIVATE));
    }

    @Test
    void requireOwner_shouldThrowForOtherUserEvenInSameFamily() {
        assertThatThrownBy(() -> ownershipGuard.requireOwner(recordOwnedBy("user-b", PrivacyLevel.SHARED)))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void loadOwned_shouldReturnEntityWhenOwned() {
        ChildProfile record = recordOwnedBy("user-a", PrivacyLevel.SHARED);
        when(repository.findById(10L)).thenReturn(Optional.of(record));

        ChildProfile loaded = ownershipGuard.loadOwned(repository, 10L);
        assertThat(loaded).isSameAs(record);
    }

    @Test
    void loadOwned_shouldThrowWhenOwnedByAnotherUser() {
        ChildProfile record = recordOwnedBy("user-b", PrivacyLevel.SHARED);
        when(repository.findById(10L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> ownershipGuard.loadOwned(repository, 10L))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void loadReadable_shouldReturnEntityWhenFamilyShared() {
        ChildProfile record = recordOwnedBy("user-b", PrivacyLevel.SHARED);
        when(repository.findById(10L)).thenReturn(Optional.of(record));
        when(familyService.getFamilyMemberIds("user-a")).thenReturn(List.of("user-a", "user-b"));

        ChildProfile loaded = ownershipGuard.loadReadable(repository, 10L);
        assertThat(loaded).isSameAs(record);
    }

    @Test
    void loadReadable_shouldThrowWhenMissingOrFamilyPrivate() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ownershipGuard.loadReadable(repository, 99L))
                .isInstanceOf(ItemNotFoundException.class);

        ChildProfile privateRecord = recordOwnedBy("user-b", PrivacyLevel.PRIVATE);
        when(repository.findById(10L)).thenReturn(Optional.of(privateRecord));
        assertThatThrownBy(() -> ownershipGuard.loadReadable(repository, 10L))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void requireReadable_shouldAllowOwnerEvenWhenPrivate() {
        ownershipGuard.requireReadable("user-a", PrivacyLevel.PRIVATE);
    }

    @Test
    void requireReadable_shouldAllowFamilyMemberWhenShared() {
        when(familyService.getFamilyMemberIds("user-a")).thenReturn(List.of("user-a", "user-b"));

        ownershipGuard.requireReadable("user-b", PrivacyLevel.SHARED);
    }

    @Test
    void requireReadable_shouldRejectFamilyMemberWhenPrivate() {
        assertThatThrownBy(() -> ownershipGuard.requireReadable("user-b", PrivacyLevel.PRIVATE))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void requireReadable_shouldRejectNonFamilyMemberWhenShared() {
        when(familyService.getFamilyMemberIds("user-a")).thenReturn(List.of("user-a"));

        assertThatThrownBy(() -> ownershipGuard.requireReadable("user-c", PrivacyLevel.SHARED))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void requireReadable_shouldRejectWhenUserHasNoFamilyGroup() {
        when(familyService.getFamilyMemberIds("user-a")).thenThrow(new IllegalStateException("No family"));

        assertThatThrownBy(() -> ownershipGuard.requireReadable("user-b", PrivacyLevel.SHARED))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void scopeResolver_shouldDefaultToPersonalOutsideHttpOrWithoutFamilyHeader() {
        ReadableScope nonHttpScope = scopeResolver.resolveReadableScope();
        assertThat(nonHttpScope.isFamily()).isFalse();
        assertThat(nonHttpScope.currentUserId()).isEqualTo("user-a");

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ReadableScope personalScope = scopeResolver.resolveReadableScope();
        assertThat(personalScope.isFamily()).isFalse();
    }

    @Test
    void scopeResolver_shouldReturnFamilyScopeWhenHeaderIsSet() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeResolver.HEADER_VIEW_SCOPE, ScopeResolver.SCOPE_FAMILY);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(familyService.getFamilyMemberIds("user-a")).thenReturn(List.of("user-a", "user-b"));

        ReadableScope scope = scopeResolver.resolveReadableScope();
        assertThat(scope.isFamily()).isTrue();
        assertThat(scope.familyMemberIds()).containsExactly("user-a", "user-b");
    }

    @Test
    void scopeResolver_shouldThrowForbiddenWhenFamilyRequestedWithoutFamilyGroup() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeResolver.HEADER_VIEW_SCOPE, ScopeResolver.SCOPE_FAMILY);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(familyService.getFamilyMemberIds("user-a")).thenThrow(new IllegalStateException("No family"));

        assertThatThrownBy(() -> scopeResolver.resolveReadableScope())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
