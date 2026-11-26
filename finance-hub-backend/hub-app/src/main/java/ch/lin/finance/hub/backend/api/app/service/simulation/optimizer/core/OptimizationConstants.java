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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;

public final class OptimizationConstants {
    private OptimizationConstants() {
    }

    // 退休年齡的合理範圍
    public static final int MIN_RETIREMENT_AGE = 50;
    public static final int MAX_RETIREMENT_AGE = 80;

    // 年金合法領取年齡範圍
    public static final int MIN_PENSION_AGE = 60;
    public static final int MAX_PENSION_AGE = 75;

    // iDeCo 合法領取年齡範圍
    public static final int MIN_IDECO_AGE = 60;
    public static final int MAX_IDECO_AGE = 75;

    // NISA 每月目標額度範圍
    public static final long MIN_NISA_MONTHLY_TARGET = 0L;
    public static final long MAX_NISA_GROWTH_TARGET = 200_000L;
    public static final long MAX_NISA_TSUMITATE_TARGET = 100_000L;
    public static final long NISA_MONTHLY_TARGET_STEP = 10_000L;

    // Grid Search 搜尋空間安全閥值
    public static final long MAX_GRID_SEARCH_SPACE = 2000L;
    public static final int MAX_GRID_ENGINE_COMBINATIONS = 5000;
}
