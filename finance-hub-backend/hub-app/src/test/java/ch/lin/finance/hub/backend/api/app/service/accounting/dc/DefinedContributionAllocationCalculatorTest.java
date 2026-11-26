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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;

class DefinedContributionAllocationCalculatorTest {

    @Test
    @DisplayName("validateAndExtractPercentages: succeeds when all required funds match and sum is 100%")
    void testValidate_WithRequiredFunds_Success() {
        List<FundAllocationCommand> allocations = List.of(
                allocation("F001", 60.0),
                allocation("F002", 40.0));

        Map<String, Double> result = DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                allocations, Set.of("F001", "F002"), "F002");

        assertThat(result).containsExactly(
                Map.entry("F001", 60.0),
                Map.entry("F002", 40.0));
    }

    @Test
    @DisplayName("validateAndExtractPercentages: succeeds when requiredFundCodes is null")
    void testValidate_WithoutRequiredFunds_Success() {
        List<FundAllocationCommand> allocations = List.of(
                allocation("F001", 33.333333),
                allocation("F002", 33.333333),
                allocation("F003", 33.333334));

        Map<String, Double> result = DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                allocations, null, "F003");

        assertThat(result).hasSize(3);
        assertThat(result.get("F003")).isEqualTo(33.333334);
    }

    @Test
    @DisplayName("validateAndExtractPercentages: rejects null/blank remainder or empty allocations")
    void testValidate_NullOrEmptyInputs() {
        List<FundAllocationCommand> valid = List.of(allocation("F001", 100.0));

        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                valid, Set.of("F001"), null));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                valid, Set.of("F001"), "   "));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                null, Set.of("F001"), "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(), Set.of("F001"), "F001"));
    }

    @Test
    @DisplayName("validateAndExtractPercentages: rejects invalid item fields and out-of-range percentages")
    void testValidate_InvalidItemValues() {
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                Collections.singletonList(null), null, "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation(" ", 100.0)), null, "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", null)), null, "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", Double.NaN)), null, "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", Double.POSITIVE_INFINITY)), null, "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", -1.0), allocation("F002", 101.0)), null, "F001"));
    }

    @Test
    @DisplayName("validateAndExtractPercentages: rejects unknown, missing, or duplicate fundCodes")
    void testValidate_FundCodeMismatch() {
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("UNKNOWN", 100.0)), Set.of("F001"), "UNKNOWN"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", 100.0)), Set.of("F001", "F002"), "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", 50.0), allocation("F001", 50.0)), Set.of("F001"), "F001"));
    }

    @Test
    @DisplayName("validateAndExtractPercentages: rejects remainderFundCode absent from allocations or sum != 100%")
    void testValidate_InvalidRemainderOrSum() {
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", 100.0)), null, "F999"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", 60.0), allocation("F002", 39.9)), null, "F002"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.validateAndExtractPercentages(
                List.of(allocation("F001", 60.0), allocation("F002", 40.1)), null, "F002"));
    }

    @Test
    @DisplayName("calculateMonthlyContributions: floors non-remainder funds and assigns remainder accurately")
    void testCalculate_FloorAndRemainder() {
        Map<String, Double> percentages = new LinkedHashMap<>();
        percentages.put("F001", 33.33);
        percentages.put("F002", 33.33);
        percentages.put("F003", 33.34);

        Map<String, Long> amounts = DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                23_000L, percentages, "F003");

        assertThat(amounts)
                .containsEntry("F001", 7_665L)
                .containsEntry("F002", 7_665L)
                .containsEntry("F003", 7_670L);
        assertThat(amounts.values().stream().mapToLong(e -> e.longValue()).sum()).isEqualTo(23_000L);
    }

    @Test
    @DisplayName("calculateMonthlyContributions: avoids IEEE-754 floor truncation on 57% of 10000")
    void testCalculate_ExactDecimalFloor() {
        Map<String, Double> percentages = Map.of("F001", 57.0, "F002", 43.0);

        Map<String, Long> amounts = DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, percentages, "F002");

        assertThat(amounts)
                .containsEntry("F001", 5_700L)
                .containsEntry("F002", 4_300L);
    }

    @Test
    @DisplayName("calculateMonthlyContributions: handles zero total, single fund, and 0% remainder absorbing 1 yen")
    void testCalculate_BoundaryCases() {
        Map<String, Long> zeroAmounts = DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                0L, Map.of("F001", 50.0, "F002", 50.0), "F001");
        assertThat(zeroAmounts).containsEntry("F001", 0L).containsEntry("F002", 0L);

        Map<String, Long> singleFund = DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                15_000L, Map.of("F001", 100.0), "F001");
        assertThat(singleFund).containsEntry("F001", 15_000L);

        Map<String, Long> zeroPctRemainder = DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_001L, Map.of("F001", 50.0, "F002", 50.0, "F003", 0.0), "F003");
        assertThat(zeroPctRemainder)
                .containsEntry("F001", 5_000L)
                .containsEntry("F002", 5_000L)
                .containsEntry("F003", 1L);
    }

    @Test
    @DisplayName("calculateMonthlyContributions: rejects negative total, empty map, missing remainder, invalid pct, or overshoot")
    void testCalculate_DefensiveGuards() {
        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                -1L, Map.of("F001", 100.0), "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, null, "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, Map.of(), "F001"));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, Map.of("F001", 100.0), null));
        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, Map.of("F001", 100.0), "UNKNOWN"));

        Map<String, Double> nullPctMap = new HashMap<>();
        nullPctMap.put("F001", null);
        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, nullPctMap, "F001"));

        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, Map.of("F001", -10.0, "F002", 100.0), "F002"));

        // Non-remainder funds sum to > 100% -> negative remainder
        assertBadRequest(() -> DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                10_000L, Map.of("F001", 60.0, "F002", 60.0, "F003", 0.0), "F003"));
    }

    private FundAllocationCommand allocation(String fundCode, Double percentage) {
        FundAllocationCommand cmd = new FundAllocationCommand();
        cmd.setFundCode(fundCode);
        cmd.setAllocationPercentage(percentage);
        return cmd;
    }

    private void assertBadRequest(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
