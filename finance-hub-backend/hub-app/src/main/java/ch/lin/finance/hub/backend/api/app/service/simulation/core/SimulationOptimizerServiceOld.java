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
package ch.lin.finance.hub.backend.api.app.service.simulation.core;

import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.command.SolveNisaGoalRequest;

/**
 * TODO: 待新版 GlobalOptimizerService 測試驗證完畢後，將此舊版最佳化介面刪除。
 */
public interface SimulationOptimizerServiceOld {

    /**
     * Calculates the optimal NISA investment goal based on the simulation request.
     *
     * @param request the request containing simulation parameters
     * @return the calculated optimal NISA goal, or null if no valid goal is found
     */
    @SuppressWarnings("deprecation")
    Long calculateNisaGoal(SolveNisaGoalRequest request);

    /**
     * Finds the best year for early mortgage repayment based on the simulation.
     *
     * @param targetAge the target age for the simulation
     * @return the best year for early repayment, or null if no early repayment is
     *         recommended
     */
    Integer findBestMortgageEarlyRepaymentYear(int targetAge);
}
