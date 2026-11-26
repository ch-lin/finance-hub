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
package ch.lin.finance.hub.backend.api.app.service.config.system.command;

import java.util.Optional;

import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a command to update an existing
 * {@link ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig}.
 * <p>
 * This is an immutable data transfer object (DTO) used to carry update
 * information. Fields are wrapped in {@link Optional} to support partial
 * updates.
 */
@Value
@Builder
public class UpdateFinanceHubConfigCommand {

    /**
     * The unique name of the configuration to update.
     */
    String name;

    /**
     * An optional new value for the enabled status of the configuration.
     */
    @Builder.Default
    Optional<Boolean> enabled = Optional.empty();

    /**
     * An optional new value for the system default currency.
     */
    @Builder.Default
    Optional<CurrencyCode> defaultCurrency = Optional.empty();

    /**
     * An optional new value for the file processing timeout in seconds.
     */
    @Builder.Default
    Optional<Integer> fileProcessingTimeoutSeconds = Optional.empty();

    /**
     * An optional new value for the NISA expected return rate.
     */
    @Builder.Default
    Optional<Double> nisaExpectedReturnRate = Optional.empty();

    /**
     * An optional new value for the iDeCo expected return rate.
     */
    @Builder.Default
    Optional<Double> idecoExpectedReturnRate = Optional.empty();

    /**
     * Expected average monthly salary for pension projection (in JPY).
     */
    @Builder.Default
    Optional<Long> pensionExpectedMonthlySalary = Optional.empty();

    /**
     * An optional new value for the life expectancy.
     */
    @Builder.Default
    Optional<Integer> lifeExpectancy = Optional.empty();

    /**
     * An optional new value for the target retirement age.
     */
    @Builder.Default
    Optional<Integer> targetRetirementAge = Optional.empty();

    @Builder.Default
    Optional<Integer> pensionStartAge = Optional.empty();

    @Builder.Default
    Optional<Integer> idecoUnlockAge = Optional.empty();

    /**
     * An optional new value for the DC unlock age.
     */
    @Builder.Default
    Optional<Integer> dcUnlockAge = Optional.empty();

    /**
     * An optional new value for the inflation rate.
     */
    @Builder.Default
    Optional<Double> inflationRate = Optional.empty();

    @Builder.Default
    Optional<Integer> gaPopulationSize = Optional.empty();
    @Builder.Default
    Optional<Integer> gaMaxGenerations = Optional.empty();
    @Builder.Default
    Optional<Double> gaMutationRate = Optional.empty();
    @Builder.Default
    Optional<Integer> gaEliteRatio = Optional.empty();
    @Builder.Default
    Optional<Integer> simulationEarlyRepaymentMonth = Optional.empty();
    @Builder.Default
    Optional<RetirementMonthMode> retirementMonthMode = Optional.empty();
}
