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
package ch.lin.finance.hub.backend.api.domain.model.base;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base entity for the Finance Hub domain that adds multi-tenancy and privacy
 * controls.
 *
 * Subclasses will inherit the ownerUserId and privacyLevel fields, and the
 * Hibernate ownerFilter will automatically restrict queries to the current
 * user's data when enabled.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
@EntityListeners(AuditingEntityListener.class)
@FilterDef(name = "ownerFilter", parameters = @ParamDef(name = "userId", type = String.class))
@FilterDef(name = "personalFilter", parameters = @ParamDef(name = "userId", type = String.class))
@Filter(name = "personalFilter", condition = "owner_user_id = :userId")
@FilterDef(name = "familyFilter", parameters = {
    @ParamDef(name = "currentUserId", type = String.class),
    @ParamDef(name = "familyUserIds", type = String.class)
})
@Filter(name = "familyFilter", condition = "owner_user_id in (:familyUserIds) and (owner_user_id = :currentUserId or privacy_level != 'PRIVATE')")
public abstract class MultiUserBaseEntity extends BaseEntity {

    public static final String OWNER_USER_ID_COLUMN = "owner_user_id";
    public static final String PRIVACY_LEVEL_COLUMN = "privacy_level";

    /**
     * The ID of the user who owns this data (mapped to the ID from the Auth
     * Service).
     */
    @CreatedBy
    @Column(name = OWNER_USER_ID_COLUMN, nullable = false, length = 36, updatable = false)
    private String ownerUserId;

    /**
     * The privacy level of this data, determining if it can be shared with
     * family members.
     */
    @lombok.Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = PRIVACY_LEVEL_COLUMN, nullable = false, length = 20)
    private PrivacyLevel privacyLevel = PrivacyLevel.SHARED;

}
