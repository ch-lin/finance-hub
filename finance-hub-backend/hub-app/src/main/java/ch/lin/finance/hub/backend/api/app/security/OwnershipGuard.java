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

import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.security.ScopeResolver.ReadableScope;
import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import lombok.RequiredArgsConstructor;

/**
 * Centralized authorization guard for multi-user resources, enforcing explicit
 * ownership checks for mutations and family/privacy checks for reads.
 */
@Component
@RequiredArgsConstructor
public class OwnershipGuard {

    private final FamilyService familyService;
    private final ScopeResolver scopeResolver;

    /**
     * Verifies that the current authenticated user owns the given entity.
     *
     * @param entity the entity to check
     */
    public void requireOwner(MultiUserBaseEntity entity) {
        if (entity == null) {
            throw new ItemNotFoundException("Resource not found");
        }
        requireOwner(entity.getOwnerUserId());
    }

    /**
     * Verifies that the current authenticated user matches {@code ownerUserId}.
     *
     * @param ownerUserId the owner user ID of the target resource
     */
    public void requireOwner(String ownerUserId) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        if (!Objects.equals(ownerUserId, currentUserId)) {
            throw new ItemNotFoundException("Resource not found");
        }
    }

    /**
     * Loads an entity by ID and verifies ownership by the current user.
     *
     * @param repository the repository to query
     * @param id         the entity ID
     * @param <T>        entity type extending {@link MultiUserBaseEntity}
     * @return the owned entity
     */
    public <T extends MultiUserBaseEntity> T loadOwned(JpaRepository<T, Long> repository, Long id) {
        T entity = repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ItemNotFoundException("Resource not found with id: " + id));
        requireOwner(entity);
        return entity;
    }

    /**
     * Loads an entity by ID and verifies read access by the current user.
     *
     * @param repository the repository to query
     * @param id         the entity ID
     * @param <T>        entity type extending {@link MultiUserBaseEntity}
     * @return the readable entity
     */
    public <T extends MultiUserBaseEntity> T loadReadable(JpaRepository<T, Long> repository, Long id) {
        T entity = repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ItemNotFoundException("Resource not found with id: " + id));
        requireReadable(entity);
        return entity;
    }

    /**
     * Verifies that the current user is allowed to read the given entity
     * (either as owner, or as a family member when privacy level is not PRIVATE).
     *
     * @param entity the entity to check
     */
    public void requireReadable(MultiUserBaseEntity entity) {
        if (entity == null) {
            throw new ItemNotFoundException("Resource not found");
        }
        requireReadable(entity.getOwnerUserId(), entity.getPrivacyLevel());
    }

    /**
     * Verifies that the current user is allowed to read a resource with the given
     * owner and privacy level.
     *
     * @param ownerUserId  the owner user ID of the resource
     * @param privacyLevel the privacy level of the resource
     */
    public void requireReadable(String ownerUserId, PrivacyLevel privacyLevel) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        if (Objects.equals(ownerUserId, currentUserId)) {
            return;
        }
        if (privacyLevel == PrivacyLevel.PRIVATE) {
            throw new ItemNotFoundException("Resource not found");
        }
        if (!isFamilyMember(currentUserId, ownerUserId)) {
            throw new ItemNotFoundException("Resource not found");
        }
    }

    /**
     * Builds a JPA {@link Specification} restricting queries to the current
     * request's {@link ReadableScope} (personal or family non-PRIVATE).
     *
     * @param <T> entity type extending {@link MultiUserBaseEntity}
     * @return the scope {@link Specification}
     */
    public <T extends MultiUserBaseEntity> Specification<T> readableSpec() {
        ReadableScope scope = scopeResolver.resolveReadableScope();
        if (!scope.isFamily()) {
            return (root, query, cb) -> cb.equal(root.get("ownerUserId"), scope.currentUserId());
        }
        return (root, query, cb) -> cb.and(
                root.get("ownerUserId").in(scope.familyMemberIds()),
                cb.or(
                        cb.equal(root.get("ownerUserId"), scope.currentUserId()),
                        cb.notEqual(root.get("privacyLevel"), PrivacyLevel.PRIVATE)));
    }

    private boolean isFamilyMember(String currentUserId, String targetOwnerId) {
        if (targetOwnerId == null) {
            return false;
        }
        try {
            return familyService.getFamilyMemberIds(currentUserId).contains(targetOwnerId);
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
