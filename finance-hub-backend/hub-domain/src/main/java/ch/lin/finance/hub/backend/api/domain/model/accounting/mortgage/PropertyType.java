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
package ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage;

/**
 * Defines the property type for a mortgage, which affects tax deduction
 * duration.
 */
public enum PropertyType {
    /** 新築一戸建て (New Detached House) */
    NEW_DETACHED_HOUSE,

    /** 中古一戸建て (Used Detached House) */
    USED_DETACHED_HOUSE,

    /** 新築マンション (New Apartment) */
    NEW_APARTMENT,

    /** 中古マンション (Used Apartment) */
    USED_APARTMENT,

    /** その他 (Other) */
    OTHER
}
