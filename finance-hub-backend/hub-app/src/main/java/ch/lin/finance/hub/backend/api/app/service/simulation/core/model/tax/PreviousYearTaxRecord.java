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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.model.tax;

/**
 * 通用跨年稅務結算紀錄。
 * 記錄前一年度真實的稅務抵扣與退稅數據，供次年度標準計算流程使用（支援確定申告退稅、住民稅轉扣及過渡年調整）。
 */
public record PreviousYearTaxRecord(
        long pendingKakuteiShinkokuRefund,
        long unusedMortgageDeductionForInhabitantTax,
        long pendingWorkingSegmentTaxAdjustment) {
}
