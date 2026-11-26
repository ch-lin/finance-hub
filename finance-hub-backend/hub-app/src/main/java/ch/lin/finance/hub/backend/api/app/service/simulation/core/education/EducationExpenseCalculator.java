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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.education;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Step 3 of the 3-Step Education Calculation Pipeline. Pure, side-effect-free
 * calculator responsible for querying the EducationAbsoluteTimeline and
 * calculating annual totals for single years or pre-computing the entire
 * timeline into an O(1) lookup table for the simulation engine.
 */
@Component
public class EducationExpenseCalculator {

    /**
     * Calculates the total education cost for a specific target calendar year.
     * Includes discrete events (tuition, entrance fee) and annual stage costs
     * (juku, living).
     *
     * @param targetYear The target calendar year (e.g., 2026).
     * @param timeline The projected absolute timeline.
     * @return The total cost for that year as a BigDecimal.
     */
    public BigDecimal calculateAnnualCost(int targetYear, EducationAbsoluteTimeline timeline) {
        if (timeline == null) {
            return BigDecimal.ZERO;
        }

        // 1. Sum discrete payment events (Tuition + Entrance Fee)
        BigDecimal eventTotal = timeline.getTotalEventCostForYear(targetYear);

        // 2. Add stage costs (Living) if active in targetYear
        BigDecimal annualAdditions = BigDecimal.ZERO;
        for (EducationAbsoluteTimeline.StageInterval interval : timeline.getStageIntervals().values()) {
            if (interval.isActiveInYear(targetYear)) {
                int stageStartM = (interval.startMonth().getYear() == targetYear) ? interval.startMonth().getMonthValue() : 1;
                int stageEndM = (interval.endMonth().getYear() == targetYear) ? interval.endMonth().getMonthValue() : 12;
                int activeMonths = stageEndM - stageStartM + 1;

                if (activeMonths > 0) {
                    if (interval.monthlyLivingCost() != null && interval.monthlyLivingCost().compareTo(BigDecimal.ZERO) > 0) {
                        annualAdditions = annualAdditions.add(interval.monthlyLivingCost().multiply(BigDecimal.valueOf(activeMonths)));
                    }
                }
            }
        }

        return eventTotal.add(annualAdditions);
    }

    /**
     * Calculates the education cost for a specific target calendar year
     * starting from fromMonth up to December. Used for the simulation
     * transition year (current calendar year) where months before fromMonth
     * have already occurred and should not be billed again against initial
     * liquid cash.
     *
     * @param targetYear The target calendar year (e.g., 2026).
     * @param fromMonth The starting month (1-12). If <= 1, calculates the
     * entire year. @param timeline The projected absolute timeline. @return
     * The total remaining cost for that year as a BigDecimal.
     */
    public BigDecimal calculateCostForRemainingYear(int targetYear, int fromMonth, EducationAbsoluteTimeline timeline) {
        if (timeline == null) {
            return BigDecimal.ZERO;
        }
        if (fromMonth <= 1) {
            return calculateAnnualCost(targetYear, timeline);
        }

        // 1. Sum discrete payment events (Tuition + Entrance Fee) where paymentMonth >= fromMonth
        BigDecimal eventTotal = BigDecimal.ZERO;
        List<EducationPaymentEvent> yearEvents = timeline.getEventsForYear(targetYear);
        if (yearEvents != null) {
            for (EducationPaymentEvent event : yearEvents) {
                if (event.getPaymentMonth() != null && event.getPaymentMonth().getMonthValue() >= fromMonth) {
                    if (event.getAmount() != null) {
                        eventTotal = eventTotal.add(event.getAmount());
                    }
                }
            }
        }

        // 2. Add stage costs (Living) prorated for remaining active months in [fromMonth, 12]
        BigDecimal additions = BigDecimal.ZERO;
        for (EducationAbsoluteTimeline.StageInterval interval : timeline.getStageIntervals().values()) {
            if (interval.isActiveInYear(targetYear)) {
                int stageStartM = (interval.startMonth().getYear() == targetYear) ? interval.startMonth().getMonthValue() : 1;
                int stageEndM = (interval.endMonth().getYear() == targetYear) ? interval.endMonth().getMonthValue() : 12;

                int activeStart = Math.max(fromMonth, stageStartM);
                int activeEnd = Math.min(12, stageEndM);
                int activeMonths = activeEnd - activeStart + 1;

                if (activeMonths > 0) {
                    if (interval.monthlyLivingCost() != null && interval.monthlyLivingCost().compareTo(BigDecimal.ZERO) > 0) {
                        additions = additions.add(interval.monthlyLivingCost().multiply(BigDecimal.valueOf(activeMonths)));
                    }
                }
            }
        }

        return eventTotal.add(additions);
    }

    /**
     * Pre-computes the entire timeline into a Map of Year -> Total Annual Cost
     * in Long. This map is cached directly in SimulationContext to enable O(1)
     * lookups during simulation loops.
     *
     * @param timeline The projected absolute timeline.
     * @return Map of Year -> Annual Total in Long.
     */
    public Map<Integer, Long> buildAnnualCostMap(EducationAbsoluteTimeline timeline) {
        return buildAnnualCostMap(timeline, null, null);
    }

    /**
     * Pre-computes the timeline into a Map of Year -> Total Annual Cost in
     * Long, taking into account the simulation transition year and starting
     * month.
     *
     * @param timeline The projected absolute timeline.
     * @param currentYear The simulation start year (transition year).
     * @param currentMonth The simulation start month (1-12).
     * @return Map of Year -> Annual Total in Long.
     */
    public Map<Integer, Long> buildAnnualCostMap(EducationAbsoluteTimeline timeline, Integer currentYear, Integer currentMonth) {
        if (timeline == null || (timeline.getEvents().isEmpty() && timeline.getStageIntervals().isEmpty())) {
            return Collections.emptyMap();
        }

        int minYear = timeline.getMinYear();
        int maxYear = timeline.getMaxYear();
        if (minYear <= 0 || maxYear <= 0 || minYear > maxYear) {
            return Collections.emptyMap();
        }

        Map<Integer, Long> costMap = new HashMap<>();
        for (int year = minYear; year <= maxYear; year++) {
            BigDecimal annualCost;
            if (currentYear != null && currentMonth != null && year == currentYear && currentMonth > 1) {
                annualCost = calculateCostForRemainingYear(year, currentMonth, timeline);
            } else {
                annualCost = calculateAnnualCost(year, timeline);
            }
            long cost = annualCost.setScale(0, RoundingMode.HALF_UP).longValue();
            if (cost > 0) {
                costMap.put(year, cost);
            }
        }

        return Collections.unmodifiableMap(costMap);
    }
}
