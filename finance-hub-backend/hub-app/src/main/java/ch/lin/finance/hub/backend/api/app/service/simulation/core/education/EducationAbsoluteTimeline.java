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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Getter;

/**
 * Container holding the complete discrete payment event stream and stage
 * intervals projected onto an absolute calendar timeline. Pre-computes HashMap
 * indexes during construction for O(1) lookups.
 */
@Getter
public class EducationAbsoluteTimeline {

    private final List<EducationPaymentEvent> events;
    private final Map<EducationStage, StageInterval> stageIntervals;

    private final Map<YearMonth, List<EducationPaymentEvent>> monthEventsMap;
    private final Map<Integer, List<EducationPaymentEvent>> yearEventsMap;
    private final Map<Integer, BigDecimal> yearTotalCostMap;
    private final int minYear;
    private final int maxYear;

    public record StageInterval(
            EducationStage stage,
            YearMonth startMonth,
            YearMonth endMonth,
            BigDecimal monthlyLivingCost) {

        public boolean isActiveInYear(int year) {
            YearMonth yearStart = YearMonth.of(year, 1);
            YearMonth yearEnd = YearMonth.of(year, 12);
            return !endMonth.isBefore(yearStart) && !startMonth.isAfter(yearEnd);
        }
    }

    @Builder
    public EducationAbsoluteTimeline(
            List<EducationPaymentEvent> events,
            Map<EducationStage, StageInterval> stageIntervals) {
        this.events = events != null ? List.copyOf(events) : Collections.emptyList();
        this.stageIntervals = stageIntervals != null ? Map.copyOf(stageIntervals) : Collections.emptyMap();

        Map<YearMonth, List<EducationPaymentEvent>> mIndex = new HashMap<>();
        Map<Integer, List<EducationPaymentEvent>> yIndex = new HashMap<>();
        Map<Integer, BigDecimal> yCost = new HashMap<>();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (EducationPaymentEvent event : this.events) {
            YearMonth ym = event.getPaymentMonth();
            int y = ym.getYear();

            mIndex.computeIfAbsent(ym, k -> new ArrayList<>()).add(event);
            yIndex.computeIfAbsent(y, k -> new ArrayList<>()).add(event);
            BigDecimal amt = event.getAmount() != null ? event.getAmount() : BigDecimal.ZERO;
            yCost.merge(y, amt, (a, b) -> a.add(b));

            if (y < min) {
                min = y;
            }
            if (y > max) {
                max = y;
            }
        }

        for (StageInterval interval : this.stageIntervals.values()) {
            int startY = interval.startMonth().getYear();
            int endY = interval.endMonth().getYear();
            if (startY < min) {
                min = startY;
            }
            if (endY > max) {
                max = endY;
            }
        }

        this.monthEventsMap = mIndex.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> e.getKey(),
                        e -> List.copyOf(e.getValue())
                ));

        this.yearEventsMap = yIndex.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> e.getKey(),
                        e -> List.copyOf(e.getValue())
                ));

        this.yearTotalCostMap = Map.copyOf(yCost);
        this.minYear = (min == Integer.MAX_VALUE) ? 0 : min;
        this.maxYear = (max == Integer.MIN_VALUE) ? 0 : max;
    }

    public List<EducationPaymentEvent> getEventsForMonth(YearMonth month) {
        if (month == null || monthEventsMap == null) {
            return Collections.emptyList();
        }
        return monthEventsMap.getOrDefault(month, Collections.emptyList());
    }

    public BigDecimal getTotalCostForMonth(YearMonth month) {
        List<EducationPaymentEvent> monthEvents = getEventsForMonth(month);
        if (monthEvents.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (EducationPaymentEvent event : monthEvents) {
            total = total.add(event.getAmount());
        }
        return total;
    }

    public List<EducationPaymentEvent> getEventsForYear(int year) {
        if (yearEventsMap == null) {
            return Collections.emptyList();
        }
        return yearEventsMap.getOrDefault(year, Collections.emptyList());
    }

    public BigDecimal getTotalEventCostForYear(int year) {
        if (yearTotalCostMap == null) {
            return BigDecimal.ZERO;
        }
        return yearTotalCostMap.getOrDefault(year, BigDecimal.ZERO);
    }

    public boolean isStageActiveInYear(EducationStage stage, int year) {
        if (stageIntervals == null || !stageIntervals.containsKey(stage)) {
            return false;
        }
        return stageIntervals.get(stage).isActiveInYear(year);
    }

    public StageInterval getStageInterval(EducationStage stage) {
        return stageIntervals != null ? stageIntervals.get(stage) : null;
    }

    public int getMinYear() {
        return minYear;
    }

    public int getMaxYear() {
        return maxYear;
    }
}
