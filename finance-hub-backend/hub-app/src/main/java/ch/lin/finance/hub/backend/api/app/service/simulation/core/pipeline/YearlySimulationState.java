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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline;

import lombok.Data;

@Data
public class YearlySimulationState {

    private long kouseiContribution = 0L;
    private long estimatedIncomeTax = 0L;
    private long estimatedInhabitantTax = 0L;
    private long estimatedSocialInsurance = 0L;
    private long netIncome = 0L;
    private long annualExpenses = 0L;
    private long pensionIncome = 0L;

}