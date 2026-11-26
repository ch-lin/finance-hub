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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;

public final class DefinedContributionAllocationCalculator {

    private static final double PERCENTAGE_SUM_TARGET = 100.0;
    private static final double PERCENTAGE_TOLERANCE = 1e-6;

    private DefinedContributionAllocationCalculator() {
    }

    public static Map<String, Double> validateAndExtractPercentages(List<FundAllocationCommand> allocations,
            Set<String> requiredFundCodes, String remainderFundCode) {
        if (remainderFundCode == null || remainderFundCode.isBlank()
                || allocations == null || allocations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "remainderFundCode and non-empty allocations are required");
        }

        Map<String, Double> requestedPercentages = new LinkedHashMap<>();
        double percentageSum = 0.0;
        for (FundAllocationCommand item : allocations) {
            if (item == null || item.getFundCode() == null || item.getFundCode().isBlank()
                    || item.getAllocationPercentage() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each allocation must include fundCode and allocationPercentage");
            }
            double pct = item.getAllocationPercentage();
            if (Double.isNaN(pct) || Double.isInfinite(pct) || pct < 0.0 || pct > PERCENTAGE_SUM_TARGET) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "allocationPercentage must be between 0 and 100");
            }
            String fundCode = item.getFundCode();
            if (requiredFundCodes != null && !requiredFundCodes.contains(fundCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown fundCode for this account: " + fundCode);
            }
            if (requestedPercentages.putIfAbsent(fundCode, pct) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Duplicate fundCode in allocations: " + fundCode);
            }
            percentageSum += pct;
        }

        if (requiredFundCodes != null && requestedPercentages.size() != requiredFundCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Allocations must include all funds in the account");
        }

        if (!requestedPercentages.containsKey(remainderFundCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "remainderFundCode does not exist in allocations: " + remainderFundCode);
        }

        if (Math.abs(percentageSum - PERCENTAGE_SUM_TARGET) > PERCENTAGE_TOLERANCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Allocation percentages must sum to 100%");
        }
        return requestedPercentages;
    }

    public static Map<String, Long> calculateMonthlyContributions(long totalContribution,
            Map<String, Double> requestedPercentages, String remainderFundCode) {
        if (totalContribution < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "totalContribution must not be negative");
        }
        if (requestedPercentages == null || requestedPercentages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "requestedPercentages must not be empty");
        }
        if (remainderFundCode == null || remainderFundCode.isBlank()
                || !requestedPercentages.containsKey(remainderFundCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "remainderFundCode must exist in requestedPercentages");
        }

        long nonRemainderSum = 0L;
        Map<String, Long> computedAmounts = new HashMap<>();
        BigDecimal totalDecimal = BigDecimal.valueOf(totalContribution);
        BigDecimal hundred = BigDecimal.valueOf(100);

        for (Map.Entry<String, Double> entry : requestedPercentages.entrySet()) {
            String code = entry.getKey();
            Double rawPct = entry.getValue();
            if (rawPct == null || Double.isNaN(rawPct) || Double.isInfinite(rawPct)
                    || rawPct < 0.0 || rawPct > PERCENTAGE_SUM_TARGET) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "allocationPercentage must be between 0 and 100");
            }
            if (!code.equals(remainderFundCode)) {
                long amount = totalDecimal
                        .multiply(BigDecimal.valueOf(rawPct))
                        .divide(hundred, 0, RoundingMode.FLOOR)
                        .longValue();
                computedAmounts.put(code, amount);
                nonRemainderSum += amount;
            }
        }

        long remainderAmount = totalContribution - nonRemainderSum;
        if (remainderAmount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Computed contribution amounts exceed account monthly total contribution");
        }
        computedAmounts.put(remainderFundCode, remainderAmount);
        return computedAmounts;
    }

}
