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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionProjectionYearlyResult;

/**
 * Service interface for calculating future pension projections based on current
 * accrued records and expected future salary data.
 */
public interface PensionProjectionService {

    /**
     * Calculates future pension projections.
     * 
     * @param userId             The ID of the user.
     * @param maxAdditionalYears The maximum number of additional working years to
     *                           project.
     * @return A list of {@link PensionProjectionYearlyResult} representing
     *         projections
     *         for 0 up to maxAdditionalYears worked.
     */
    List<PensionProjectionYearlyResult> calculateProjection(String userId, int maxAdditionalYears,
            Integer pensionStartAgeOverride);

}
