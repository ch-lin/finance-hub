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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EducationTimelineProjectorTest {

    private EducationTimelineProjector projector;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        projector = new EducationTimelineProjector();
    }

    @Test
    @DisplayName("Should project primary school with entrance fee and semester tuitions")
    void shouldProjectPrimarySchool() {
        LocalDate birthDate = LocalDate.of(2017, 8, 15);

        ResolvedStageData primary = ResolvedStageData.builder()
                .stage(EducationStage.PRIMARY)
                .trackName("PUBLIC")
                .active(true)
                .annualTuition(new BigDecimal("60000"))
                .durationMonths(72)
                .startMonth(4)
                .termsPerYear(2)
                .entranceFee(new BigDecimal("10000"))
                .paymentMonths(List.of(4, 10))
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(EducationStage.PRIMARY, primary))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        // Standard Japanese primary starts at 2024-04 (child turns 6 in 2023-08, enters next April)
        EducationAbsoluteTimeline.StageInterval interval = timeline.getStageInterval(EducationStage.PRIMARY);
        assertThat(interval).isNotNull();
        assertThat(interval.startMonth()).isEqualTo(YearMonth.of(2024, 4));
        assertThat(interval.endMonth()).isEqualTo(YearMonth.of(2030, 3)); // 72 months

        // Check events: 1 entrance fee + 12 tuition payments (2 per year for 6 years)
        List<EducationPaymentEvent> events = timeline.getEvents();
        assertThat(events).hasSize(13);

        // Entrance fee at start
        EducationPaymentEvent entranceFeeEvent = events.stream()
                .filter(e -> e.getType() == PaymentType.ENTRANCE_FEE)
                .findFirst()
                .orElseThrow();
        assertThat(entranceFeeEvent.getPaymentMonth()).isEqualTo(YearMonth.of(2024, 4));
        assertThat(entranceFeeEvent.getAmount()).isEqualByComparingTo("10000");

        // First tuition in 2024-04
        List<EducationPaymentEvent> april2024Events = timeline.getEventsForMonth(YearMonth.of(2024, 4));
        assertThat(april2024Events).hasSize(2); // Entrance fee + Tuition

        // Last tuition in 2029-10
        EducationPaymentEvent lastTuition = events.get(events.size() - 1);
        assertThat(lastTuition.getPaymentMonth()).isEqualTo(YearMonth.of(2029, 10));
        assertThat(lastTuition.getAmount()).isEqualByComparingTo("30000"); // 60000 / 2
    }

    @Test
    @DisplayName("Should correctly handle transition year from Japanese primary (April) to International middle (September)")
    void shouldHandleTransitionYearCorrectly() {
        LocalDate birthDate = LocalDate.of(2017, 8, 15);

        // Primary: 2024-04 to 2030-03 (72 months, starts April)
        ResolvedStageData primary = ResolvedStageData.builder()
                .stage(EducationStage.PRIMARY)
                .trackName("PUBLIC")
                .active(true)
                .annualTuition(new BigDecimal("60000"))
                .durationMonths(72)
                .startMonth(4)
                .termsPerYear(2)
                .entranceFee(BigDecimal.ZERO)
                .paymentMonths(List.of(4, 10))
                .build();

        // Middle: 36 months, starts September (Intl school)
        ResolvedStageData middle = ResolvedStageData.builder()
                .stage(EducationStage.MIDDLE)
                .trackName("INTL")
                .active(true)
                .annualTuition(new BigDecimal("1800000"))
                .durationMonths(36)
                .startMonth(9)
                .termsPerYear(3)
                .entranceFee(new BigDecimal("300000"))
                .paymentMonths(List.of(9, 1, 5))
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(
                        EducationStage.PRIMARY, primary,
                        EducationStage.MIDDLE, middle))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        EducationAbsoluteTimeline.StageInterval primaryInterval = timeline.getStageInterval(EducationStage.PRIMARY);
        EducationAbsoluteTimeline.StageInterval middleInterval = timeline.getStageInterval(EducationStage.MIDDLE);

        assertThat(primaryInterval.startMonth()).isEqualTo(YearMonth.of(2024, 4));
        assertThat(primaryInterval.endMonth()).isEqualTo(YearMonth.of(2030, 3));

        // Middle school chains after primary ends (2030-03) to next September -> 2030-09
        assertThat(middleInterval.startMonth()).isEqualTo(YearMonth.of(2030, 9));
        assertThat(middleInterval.endMonth()).isEqualTo(YearMonth.of(2033, 8)); // 36 months

        // In transition year 2030:
        // Summer gap: months 4, 5, 6, 7, 8 have NO events
        for (int m = 4; m <= 8; m++) {
            assertThat(timeline.getEventsForMonth(YearMonth.of(2030, m))).isEmpty();
        }

        // September 2030 has entrance fee (300k) + first trimester tuition (600k)
        List<EducationPaymentEvent> sept2030Events = timeline.getEventsForMonth(YearMonth.of(2030, 9));
        assertThat(sept2030Events).hasSize(2);
        assertThat(timeline.getTotalCostForMonth(YearMonth.of(2030, 9))).isEqualByComparingTo("900000");
    }

    @Test
    @DisplayName("Should preserve university final months without truncation")
    void shouldPreserveUniversityFinalMonths() {
        LocalDate birthDate = LocalDate.of(2010, 10, 10);

        // UK University: 3 years (36 months), starts 2029-09
        ResolvedStageData uni = ResolvedStageData.builder()
                .stage(EducationStage.UNIVERSITY)
                .trackName("UK")
                .active(true)
                .annualTuition(new BigDecimal("3000000"))
                .durationMonths(36)
                .startMonth(9)
                .termsPerYear(3)
                .paymentMonths(List.of(9, 1, 5))
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(EducationStage.UNIVERSITY, uni))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        EducationAbsoluteTimeline.StageInterval interval = timeline.getStageInterval(EducationStage.UNIVERSITY);
        // Born 2010-10 (after Sept cutoff), academicBirthYear 2011, age 18 -> starts 2029-09
        assertThat(interval.startMonth()).isEqualTo(YearMonth.of(2029, 9));
        assertThat(interval.endMonth()).isEqualTo(YearMonth.of(2032, 8)); // 36 months -> ends August 2032!

        // Final academic year spans 2031-09 ~ 2032-08
        // Payment in January 2032:
        List<EducationPaymentEvent> jan2032 = timeline.getEventsForMonth(YearMonth.of(2032, 1));
        assertThat(jan2032).hasSize(1);
        assertThat(jan2032.get(0).getAmount()).isEqualByComparingTo("1000000"); // 3000000 / 3

        // Payment in May 2032:
        List<EducationPaymentEvent> may2032 = timeline.getEventsForMonth(YearMonth.of(2032, 5));
        assertThat(may2032).hasSize(1);
        assertThat(may2032.get(0).getAmount()).isEqualByComparingTo("1000000");

        // Total payments in 2032 (the final graduation calendar year)
        assertThat(timeline.getTotalEventCostForYear(2032)).isEqualByComparingTo("2000000");
    }

    @Test
    @DisplayName("Should skip preschool when set to NONE and start directly with primary")
    void shouldSkipPreschoolWhenNone() {
        LocalDate birthDate = LocalDate.of(2018, 1, 10); // 早生まれ (Jan 10)

        ResolvedStageData preschool = ResolvedStageData.builder()
                .stage(EducationStage.PRESCHOOL)
                .trackName("NONE")
                .active(false)
                .build();

        ResolvedStageData primary = ResolvedStageData.builder()
                .stage(EducationStage.PRIMARY)
                .trackName("PUBLIC")
                .active(true)
                .annualTuition(new BigDecimal("50000"))
                .durationMonths(72)
                .startMonth(4)
                .termsPerYear(2)
                .paymentMonths(List.of(4, 10))
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(
                        EducationStage.PRESCHOOL, preschool,
                        EducationStage.PRIMARY, primary))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        assertThat(timeline.getStageInterval(EducationStage.PRESCHOOL)).isNull();

        // Born 2018-01 (Jan), turns 6 in 2024-01 -> enters primary 2024-04!
        EducationAbsoluteTimeline.StageInterval primaryInterval = timeline.getStageInterval(EducationStage.PRIMARY);
        assertThat(primaryInterval).isNotNull();
        assertThat(primaryInterval.startMonth()).isEqualTo(YearMonth.of(2024, 4));
        assertThat(primaryInterval.endMonth()).isEqualTo(YearMonth.of(2030, 3));
    }

    @Test
    @DisplayName("Should project two-stage preschool (HOME 12 mos + INTL 24 mos) with entrance fee at cutover month and seamless primary chaining")
    void shouldProjectTwoStagePreschoolHomeThenIntl() {
        LocalDate birthDate = LocalDate.of(2021, 8, 15);

        // Stage 2: INTL (24 mos, entrance fee 200k, tuition 1.5M/yr, payments in Sep & Mar)
        ResolvedStageData stage2 = ResolvedStageData.builder()
                .stage(EducationStage.PRESCHOOL)
                .trackName("INTL")
                .active(true)
                .annualTuition(new BigDecimal("1500000"))
                .durationMonths(24)
                .startMonth(9)
                .termsPerYear(2)
                .entranceFee(new BigDecimal("200000"))
                .paymentMonths(List.of(9, 3))
                .build();

        // Stage 1: HOME (12 mos, 0 tuition, 0 entrance fee)
        ResolvedStageData preschool = ResolvedStageData.builder()
                .stage(EducationStage.PRESCHOOL)
                .trackName("HOME")
                .active(true)
                .annualTuition(BigDecimal.ZERO)
                .durationMonths(12)
                .startMonth(4)
                .termsPerYear(2)
                .entranceFee(BigDecimal.ZERO)
                .paymentMonths(List.of(4, 10))
                .stage2(stage2)
                .build();

        // Primary: 72 mos, starts April 2028
        ResolvedStageData primary = ResolvedStageData.builder()
                .stage(EducationStage.PRIMARY)
                .trackName("PUBLIC")
                .active(true)
                .annualTuition(new BigDecimal("60000"))
                .durationMonths(72)
                .startMonth(4)
                .termsPerYear(2)
                .paymentMonths(List.of(4, 10))
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(
                        EducationStage.PRESCHOOL, preschool,
                        EducationStage.PRIMARY, primary))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        // 1. Preschool stage interval: spans full 36 months (2025-04 to 2028-03)
        EducationAbsoluteTimeline.StageInterval preschoolInterval = timeline.getStageInterval(EducationStage.PRESCHOOL);
        assertThat(preschoolInterval).isNotNull();
        assertThat(preschoolInterval.startMonth()).isEqualTo(YearMonth.of(2025, 4));
        assertThat(preschoolInterval.endMonth()).isEqualTo(YearMonth.of(2028, 3));

        // 2. Primary chains seamlessly at 2028-04
        EducationAbsoluteTimeline.StageInterval primaryInterval = timeline.getStageInterval(EducationStage.PRIMARY);
        assertThat(primaryInterval).isNotNull();
        assertThat(primaryInterval.startMonth()).isEqualTo(YearMonth.of(2028, 4));

        // 3. Stage 1 (2025-04 to 2026-03): HOME has zero payment events
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(2025, 4).plusMonths(m - 1);
            assertThat(timeline.getEventsForMonth(ym)).isEmpty();
        }

        // 4. Cutover month (2026-04): Stage 2 INTL entrance fee (200k)
        List<EducationPaymentEvent> cutoverEvents = timeline.getEventsForMonth(YearMonth.of(2026, 4));
        assertThat(cutoverEvents).hasSize(1);
        assertThat(cutoverEvents.get(0).getType()).isEqualTo(PaymentType.ENTRANCE_FEE);
        assertThat(cutoverEvents.get(0).getAmount()).isEqualByComparingTo("200000");

        // 5. Stage 2 tuitions: 4 payments (750k each) across 24 months
        List<EducationPaymentEvent> preschoolEvents = timeline.getEvents().stream()
                .filter(e -> e.getStage() == EducationStage.PRESCHOOL)
                .toList();
        assertThat(preschoolEvents).hasSize(5); // 1 entrance fee + 4 tuitions

        List<EducationPaymentEvent> tuitionEvents = preschoolEvents.stream()
                .filter(e -> e.getType() == PaymentType.TUITION)
                .toList();
        assertThat(tuitionEvents).hasSize(4);
        assertThat(tuitionEvents.get(0).getPaymentMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(tuitionEvents.get(0).getAmount()).isEqualByComparingTo("750000");
        assertThat(tuitionEvents.get(1).getPaymentMonth()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(tuitionEvents.get(1).getAmount()).isEqualByComparingTo("750000");
        assertThat(tuitionEvents.get(2).getPaymentMonth()).isEqualTo(YearMonth.of(2027, 9));
        assertThat(tuitionEvents.get(2).getAmount()).isEqualByComparingTo("750000");
        assertThat(tuitionEvents.get(3).getPaymentMonth()).isEqualTo(YearMonth.of(2028, 3));
        assertThat(tuitionEvents.get(3).getAmount()).isEqualByComparingTo("750000");
    }

    @Test
    @DisplayName("Should project two-stage preschool with DAYCARE stage 1 (12 mos) and INTL stage 2 (24 mos)")
    void shouldProjectTwoStagePreschoolDaycareThenIntl() {
        LocalDate birthDate = LocalDate.of(2021, 8, 15);

        ResolvedStageData stage2 = ResolvedStageData.builder()
                .stage(EducationStage.PRESCHOOL)
                .trackName("INTL")
                .active(true)
                .annualTuition(new BigDecimal("1500000"))
                .durationMonths(24)
                .startMonth(9)
                .termsPerYear(2)
                .entranceFee(new BigDecimal("200000"))
                .paymentMonths(List.of(9, 3))
                .build();

        ResolvedStageData preschool = ResolvedStageData.builder()
                .stage(EducationStage.PRESCHOOL)
                .trackName("DAYCARE")
                .active(true)
                .annualTuition(new BigDecimal("400000"))
                .durationMonths(12)
                .startMonth(4)
                .termsPerYear(2)
                .entranceFee(BigDecimal.ZERO)
                .paymentMonths(List.of(4, 10))
                .stage2(stage2)
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(EducationStage.PRESCHOOL, preschool))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        // Stage 1 DAYCARE tuitions: 2025-04 (200k) and 2025-10 (200k)
        List<EducationPaymentEvent> april2025 = timeline.getEventsForMonth(YearMonth.of(2025, 4));
        assertThat(april2025).hasSize(1);
        assertThat(april2025.get(0).getAmount()).isEqualByComparingTo("200000");

        List<EducationPaymentEvent> oct2025 = timeline.getEventsForMonth(YearMonth.of(2025, 10));
        assertThat(oct2025).hasSize(1);
        assertThat(oct2025.get(0).getAmount()).isEqualByComparingTo("200000");

        // Cutover month 2026-04: INTL entrance fee (200k)
        List<EducationPaymentEvent> cutover = timeline.getEventsForMonth(YearMonth.of(2026, 4));
        assertThat(cutover).hasSize(1);
        assertThat(cutover.get(0).getType()).isEqualTo(PaymentType.ENTRANCE_FEE);
        assertThat(cutover.get(0).getAmount()).isEqualByComparingTo("200000");

        // Total events: 2 Daycare tuitions + 1 INTL entrance fee + 4 INTL tuitions = 7 events
        assertThat(timeline.getEvents()).hasSize(7);
    }

    @Test
    @DisplayName("Should project single-track DAYCARE at age 3 by default instead of legacy age 1")
    void shouldProjectSingleTrackDaycareAtAge3ByDefault() {
        LocalDate birthDate = LocalDate.of(2021, 8, 15);

        ResolvedStageData daycare = ResolvedStageData.builder()
                .stage(EducationStage.PRESCHOOL)
                .trackName("DAYCARE")
                .active(true)
                .annualTuition(new BigDecimal("400000"))
                .durationMonths(36)
                .startMonth(4)
                .termsPerYear(2)
                .entranceFee(BigDecimal.ZERO)
                .paymentMonths(List.of(4, 10))
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(EducationStage.PRESCHOOL, daycare))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        // Academic birth year for 2021-08-15 (startMonth=4) is 2022.
        // Entrance age 3 -> starts 2025-04 (NOT 2023-04).
        assertThat(timeline.getStageIntervals()).containsKey(EducationStage.PRESCHOOL);
        EducationAbsoluteTimeline.StageInterval interval = timeline.getStageIntervals().get(EducationStage.PRESCHOOL);
        assertThat(interval.startMonth()).isEqualTo(YearMonth.of(2025, 4));
        assertThat(interval.endMonth()).isEqualTo(YearMonth.of(2028, 3));
    }

    @Test
    @DisplayName("Should dynamically use standardEntranceAge configured on ResolvedStageData")
    void shouldRespectDynamicStandardEntranceAgeFromStageData() {
        LocalDate birthDate = LocalDate.of(2021, 8, 15);

        ResolvedStageData customStage = ResolvedStageData.builder()
                .stage(EducationStage.PRIMARY)
                .trackName("CUSTOM_TRACK")
                .active(true)
                .annualTuition(new BigDecimal("100000"))
                .durationMonths(36)
                .startMonth(4)
                .termsPerYear(1)
                .standardEntranceAge(7) // custom entrance age: 7 instead of standard 6
                .paymentMonths(List.of(4))
                .build();

        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
                .stages(Map.of(EducationStage.PRIMARY, customStage))
                .build();

        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);

        // Academic birth year 2022 + 7 = 2029-04
        EducationAbsoluteTimeline.StageInterval interval = timeline.getStageIntervals().get(EducationStage.PRIMARY);
        assertThat(interval.startMonth()).isEqualTo(YearMonth.of(2029, 4));
    }
}
