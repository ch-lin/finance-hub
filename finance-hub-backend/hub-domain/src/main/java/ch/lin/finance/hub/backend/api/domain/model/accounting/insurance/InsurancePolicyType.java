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
package ch.lin.finance.hub.backend.api.domain.model.accounting.insurance;

public enum InsurancePolicyType {
    /** Tax deduction: 一般生命保険料控除 (General Life Insurance Premium Deduction) */
    LIFE,
    /**
     * Tax deduction: 介護医療保険料控除 (Nursing Care/Medical Insurance Premium Deduction)
     */
    NURSING_CARE,
    /**
     * Tax deduction: 介護医療保険料控除 (Nursing Care/Medical Insurance Premium Deduction)
     */
    HEALTH,
    /**
     * Tax deduction: 介護医療保険料控除 (Nursing Care/Medical Insurance Premium Deduction)
     */
    CANCER,
    /** Tax deduction: 一般生命保険料控除 (General Life Insurance Premium Deduction) */
    ENDOWMENT,
    /**
     * Tax deduction: 介護医療保険料控除 (Nursing Care/Medical Insurance Premium Deduction)
     */
    MEDICAL,
    /** Tax deduction: 個人年金保険料控除 (Personal Pension Insurance Premium Deduction) */
    PENSION,
    /** No tax deduction usually */
    FIRE,
    /** Tax deduction: 地震保険料控除 (Earthquake Insurance Premium Deduction) */
    EARTHQUAKE,
    /** No tax deduction */
    AUTO,
    /** No tax deduction */
    OTHER
}
