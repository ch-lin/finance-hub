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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @deprecated
 *             TODO: 舊版的最佳化處理器因為新版的基因演算法 (GA) 最佳化引擎已經上線，
 *             待新版測試完成且穩定後，包含此 Request 物件與整個舊版 Optimizer 都將被全面移除。
 *             因此暫時不花費工時將此 Controller DTO 重構搬移回 hub 模組。
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolveNisaGoalRequest {
    @NotNull
    @Min(value = 0, message = "Target age must be >= 0")
    @Max(value = 120, message = "Target age must be <= 120")
    private Integer targetAge;

    @Min(value = 0, message = "Safety net amount must be >= 0")
    private Long safetyNetAmount;

    @Min(value = -1, message = "Inflation rate must be >= -1.0 (-100%)")
    @Max(value = 10, message = "Inflation rate must be <= 10.0 (1000%)")
    private Double overrideInflationRate;

    @Min(value = -1, message = "NISA return rate must be >= -1.0 (-100%)")
    @Max(value = 10, message = "NISA return rate must be <= 10.0 (1000%)")
    private Double overrideNisaReturnRate;
}
