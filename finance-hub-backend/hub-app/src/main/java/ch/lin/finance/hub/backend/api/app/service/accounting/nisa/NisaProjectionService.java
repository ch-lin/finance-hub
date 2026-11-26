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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaProjectionYearlyResult;

/**
 * Service interface for calculating future NISA portfolio projections.
 */
public interface NisaProjectionService {

    /**
     * Calculates the projected value of the NISA portfolio over the specified
     * number of years.
     *
     * @param yearsToProject The number of years into the future to project.
     * @return A list of yearly projections.
     */
    List<NisaProjectionYearlyResult> calculateProjection(int yearsToProject);
}
