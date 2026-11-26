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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Step 2 of the 3-Step Education Calculation Pipeline. Projects flattened
 * ResolvedEducationProfile data onto an absolute calendar timeline based on the
 * child's birth date using the "Sequential Block Chaining" algorithm.
 */
@Component
public class EducationTimelineProjector {

    public static final int PRESCHOOL_AGE = 3;
    public static final int PRIMARY_AGE = 6;
    public static final int MIDDLE_AGE = 12;
    public static final int HIGH_AGE = 15;
    public static final int UNIVERSITY_AGE = 18;

    /**
     * Projects the child's academic career onto an absolute calendar timeline.
     *
     * @param birthDate The child's date of birth.
     * @param profile The resolved education profile with flattened stage
     * parameters.
     * @return An EducationAbsoluteTimeline containing discrete payment events
     * and stage intervals.
     */
    public EducationAbsoluteTimeline project(LocalDate birthDate, ResolvedEducationProfile profile) {
        if (birthDate == null || profile == null) {
            return EducationAbsoluteTimeline.builder().build();
        }

        List<EducationPaymentEvent> events = new ArrayList<>();
        Map<EducationStage, EducationAbsoluteTimeline.StageInterval> intervals = new EnumMap<>(EducationStage.class);

        YearMonth previousStageEnd = null;

        for (EducationStage stage : EducationStage.values()) {
            ResolvedStageData stageData = profile.getStage(stage);
            if (stageData == null || !stageData.isActive() || stageData.getDurationMonths() <= 0) {
                continue;
            }

            YearMonth stageStart = calculateStageStart(birthDate, stage, stageData, previousStageEnd);
            int totalDuration = stageData.getDurationMonths();
            if (stageData.hasStage2()) {
                totalDuration += stageData.getStage2().getDurationMonths();
            }
            YearMonth stageEnd = stageStart.plusMonths(totalDuration - 1);

            intervals.put(stage, new EducationAbsoluteTimeline.StageInterval(
                    stage,
                    stageStart,
                    stageEnd,
                    stageData.getMonthlyLivingCost()
            ));

            generateStageEvents(stage, stageData, stageStart, stageEnd, events);

            previousStageEnd = stageEnd;
        }

        Collections.sort(events);

        return EducationAbsoluteTimeline.builder()
                .events(Collections.unmodifiableList(events))
                .stageIntervals(Collections.unmodifiableMap(intervals))
                .build();
    }

    private YearMonth calculateStageStart(
            LocalDate birthDate,
            EducationStage stage,
            ResolvedStageData stageData,
            YearMonth previousStageEnd) {

        Integer configuredMonth = stageData.getStartMonth();
        int startMonth = configuredMonth != null ? configuredMonth : 4;
        YearMonth standardStart = calculateStandardStartYearMonth(birthDate, stage, stageData, startMonth);

        if (previousStageEnd == null) {
            return standardStart;
        }

        YearMonth earliestNext = previousStageEnd.plusMonths(1);
        YearMonth chainedStart = calculateNextStartMonth(earliestNext, startMonth);

        // A stage cannot start earlier than its standard age entrance unless chained
        return chainedStart.isAfter(standardStart) ? chainedStart : standardStart;
    }

    private YearMonth calculateStandardStartYearMonth(
            LocalDate birthDate,
            EducationStage stage,
            ResolvedStageData stageData,
            int startMonth) {

        int academicBirthYear = computeAcademicBirthYear(birthDate, startMonth);
        int targetAge = getStandardEntranceAge(stage, stageData);
        return YearMonth.of(academicBirthYear + targetAge, startMonth);
    }

    private int getStandardEntranceAge(EducationStage stage, ResolvedStageData stageData) {
        if (stageData != null && stageData.getStandardEntranceAge() != null) {
            return stageData.getStandardEntranceAge();
        }
        return switch (stage) {
            case PRESCHOOL ->
                PRESCHOOL_AGE;
            case PRIMARY ->
                PRIMARY_AGE;
            case MIDDLE ->
                MIDDLE_AGE;
            case HIGH ->
                HIGH_AGE;
            case UNIVERSITY ->
                UNIVERSITY_AGE;
        };
    }

    /**
     * Determines the child's academic birth year cohort given their birth date
     * and the entrance start month.
     * <p>
     * <b>Academic Cutoff Rule (cutoffDay = 1):</b>
     * <ul>
     * <li>Born on or before {@code startMonth}-01: assigned to
     * {@code birthYear} (current cohort).</li>
     * <li>Born on or after {@code startMonth}-02: assigned to
     * {@code birthYear + 1} (subsequent cohort).</li>
     * </ul>
     * <p>
     * <b>Real-World Legal & Jurisdictional Alignment:</b>
     * <ul>
     * <li><b>Japan (startMonth = 4):</b> Strictly complies with the Japanese
     * School Education Act (学校教育法施行規則) regarding <i>hayaumare</i> (早生まれ). A
     * child born on April 1 reaches age 6 on the eve (March 31) and belongs to
     * the current cohort; children born April 2 or later belong to the next
     * cohort.</li>
     * <li><b>Taiwan (startMonth = 9):</b> Strictly complies with Taiwan's
     * Compulsory Education Act (國民教育法), where September 1 is the statutory
     * cutoff date (Sep 1 belongs to the current cohort; Sep 2+ enters the
     * following year).</li>
     * <li><b>United States (startMonth = 9):</b> Conforms to the dominant
     * Kindergarten cutoff date adopted by the majority of US states (including
     * California, Texas, Florida, and Washington).</li>
     * </ul>
     * <p>
     * <i>Note on Edge Cases:</i> A minority of jurisdictions use slight
     * variations (e.g. UK uses Aug 31, differing by 1 day on Sep 1 births; NYC
     * uses calendar year Dec 31). For macro financial asset projections, this
     * unified formula achieves optimal cross-border accuracy without
     * introducing redundant per-child cutoff configuration.
     *
     * @param birthDate The child's birth date (non-null).
     * @param startMonth The academic track's entrance start month (e.g., 4 for
     * Japan, 9 for Taiwan/US/Intl).
     * @return The academic cohort year to which the child belongs.
     */
    public static int computeAcademicBirthYear(LocalDate birthDate, int startMonth) {
        int birthYear = birthDate.getYear();
        int birthMonth = birthDate.getMonthValue();
        int birthDay = birthDate.getDayOfMonth();

        int cutoffDay = 1;
        if (birthMonth > startMonth || (birthMonth == startMonth && birthDay > cutoffDay)) {
            return birthYear + 1;
        }
        return birthYear;
    }

    /**
     * Finds the earliest calendar month matching targetStartMonth that is on or
     * after afterMonth.
     */
    public static YearMonth calculateNextStartMonth(YearMonth afterMonth, int targetStartMonth) {
        YearMonth candidate = YearMonth.of(afterMonth.getYear(), targetStartMonth);
        if (!candidate.isBefore(afterMonth)) {
            return candidate;
        }
        return YearMonth.of(afterMonth.getYear() + 1, targetStartMonth);
    }

    private void generateStageEvents(
            EducationStage stage,
            ResolvedStageData stageData,
            YearMonth stageStart,
            YearMonth stageEnd,
            List<EducationPaymentEvent> events) {

        if (!stageData.hasStage2()) {
            generateSegmentEvents(stage, stageData, stageStart, stageEnd, events);
            return;
        }

        // Two-stage preschool event generation
        YearMonth stage1End = stageStart.plusMonths(stageData.getDurationMonths() - 1);
        generateSegmentEvents(stage, stageData, stageStart, stage1End, events);

        YearMonth stage2Start = stageStart.plusMonths(stageData.getDurationMonths());
        generateSegmentEvents(stage, stageData.getStage2(), stage2Start, stageEnd, events);
    }

    private void generateSegmentEvents(
            EducationStage stage,
            ResolvedStageData segmentData,
            YearMonth segmentStart,
            YearMonth segmentEnd,
            List<EducationPaymentEvent> events) {

        // 1. One-time entrance fee at segment start
        BigDecimal entranceFee = segmentData.getEntranceFee();
        if (entranceFee != null && entranceFee.compareTo(BigDecimal.ZERO) > 0) {
            events.add(EducationPaymentEvent.builder()
                    .paymentMonth(segmentStart)
                    .amount(entranceFee)
                    .type(PaymentType.ENTRANCE_FEE)
                    .stage(stage)
                    .build());
        }

        // 2. Periodic term tuitions
        BigDecimal annualTuition = segmentData.getAnnualTuition();
        int termsPerYear = segmentData.getTermsPerYear();
        List<Integer> paymentMonths = segmentData.getPaymentMonths();

        if (annualTuition != null && annualTuition.compareTo(BigDecimal.ZERO) > 0 && termsPerYear > 0) {
            BigDecimal termTuition = annualTuition.divide(BigDecimal.valueOf(termsPerYear), 4, RoundingMode.HALF_UP);

            YearMonth cursor = segmentStart;
            while (!cursor.isAfter(segmentEnd)) {
                if (paymentMonths.contains(cursor.getMonthValue())) {
                    events.add(EducationPaymentEvent.builder()
                            .paymentMonth(cursor)
                            .amount(termTuition)
                            .type(PaymentType.TUITION)
                            .stage(stage)
                            .build());
                }
                cursor = cursor.plusMonths(1);
            }
        }
    }
}
