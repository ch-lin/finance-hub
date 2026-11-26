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

import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base entity for models that require privacy control (e.g. ecommerce items).
 * Provides a privacy level that dictates whether family members can see it or its details.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class PrivacyOverrideBaseEntity extends BaseEntity {

    /**
     * The overridden privacy level for this entity. Defaults to null (fallback to parent).
     */
    @Column(name = "privacy_level", nullable = true)
    @Enumerated(EnumType.STRING)
    private PrivacyLevel privacyLevel;
}
