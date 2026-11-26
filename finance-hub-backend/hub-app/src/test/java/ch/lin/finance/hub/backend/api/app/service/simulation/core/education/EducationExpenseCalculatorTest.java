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

class EducationExpenseCalculatorTest {

    private EducationExpenseCalculator calculator;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        calculator = new EducationExpenseCalculator();
    }

    @Test
    @DisplayName("Should correctly calculate annual cost combining discrete events and stage living cost")
    void shouldCalculateAnnualCostWithLivingCost() {
        // Timeline in 2026:
        // Discrete events: 2026-04 Tuition 500k, 2026-10 Tuition 500k
        // Stage interval: High school active in 2026 (no living cost)
        EducationPaymentEvent event1 = EducationPaymentEvent.builder()
                .paymentMonth(YearMonth.of(2026, 4))
                .amount(new BigDecimal("500000"))
                .type(PaymentType.TUITION)
                .stage(EducationStage.HIGH)
                .build();

        EducationPaymentEvent event2 = EducationPaymentEvent.builder()
                .paymentMonth(YearMonth.of(2026, 10))
                .amount(new BigDecimal("500000"))
                .type(PaymentType.TUITION)
                .stage(EducationStage.HIGH)
                .build();

        EducationAbsoluteTimeline.StageInterval highInterval = new EducationAbsoluteTimeline.StageInterval(
                EducationStage.HIGH,
                YearMonth.of(2026, 4),
                YearMonth.of(2029, 3),
                BigDecimal.ZERO // living
        );

        EducationAbsoluteTimeline timeline = EducationAbsoluteTimeline.builder()
                .events(List.of(event1, event2))
                .stageIntervals(Map.of(EducationStage.HIGH, highInterval))
                .build();

        // 2026 Total: 500k + 500k = 1,000,000
        BigDecimal cost2026 = calculator.calculateAnnualCost(2026, timeline);
        assertThat(cost2026).isEqualByComparingTo("1000000");

        // 2025 Total: inactive -> 0
        BigDecimal cost2025 = calculator.calculateAnnualCost(2025, timeline);
        assertThat(cost2025).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should build complete pre-computed annual cost map for O(1) simulation lookup")
    void shouldBuildAnnualCostMap() {
        EducationTimelineProjector projector = new EducationTimelineProjector();
        LocalDate birthDate = LocalDate.of(2017, 8, 15);

        // Child with Primary (2024-04 ~ 2030-03, 60k tuition/yr)
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
        Map<Integer, Long> costMap = calculator.buildAnnualCostMap(timeline);

        // 2024: entrance fee (10k) + 2024-04 (30k) + 2024-10 (30k) = 70,000
        assertThat(costMap.get(2024)).isEqualTo(70000L);

        // 2025 ~ 2029: 2 terms x 30k = 60,000
        for (int y = 2025; y <= 2029; y++) {
            assertThat(costMap.get(y)).isEqualTo(60000L);
        }

        // Before 2024: 0
        assertThat(costMap.getOrDefault(2023, 0L)).isEqualTo(0L);

        // After graduation (2031): 0
        assertThat(costMap.getOrDefault(2031, 0L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should accurately calculate remaining year cost by excluding past events and prorating stage costs")
    void shouldCalculateCostForRemainingYear_excludingPastEventsAndProratingStageCosts() {
        // Timeline in 2026:
        // Discrete events:
        //   - 2026-04 Entrance Fee: 300,000 (past event, should be excluded if simulation starts in Sep)
        //   - 2026-04 Tuition: 500,000 (past event, should be excluded if simulation starts in Sep)
        //   - 2026-10 Tuition: 500,000 (future event, should be included)
        // Stage interval:
        //   - High school 2026-04 ~ 2029-03, monthly living cost: 30,000
        EducationPaymentEvent entranceFee = EducationPaymentEvent.builder()
                .paymentMonth(YearMonth.of(2026, 4))
                .amount(new BigDecimal("300000"))
                .type(PaymentType.ENTRANCE_FEE)
                .stage(EducationStage.HIGH)
                .build();

        EducationPaymentEvent term1 = EducationPaymentEvent.builder()
                .paymentMonth(YearMonth.of(2026, 4))
                .amount(new BigDecimal("500000"))
                .type(PaymentType.TUITION)
                .stage(EducationStage.HIGH)
                .build();

        EducationPaymentEvent term2 = EducationPaymentEvent.builder()
                .paymentMonth(YearMonth.of(2026, 10))
                .amount(new BigDecimal("500000"))
                .type(PaymentType.TUITION)
                .stage(EducationStage.HIGH)
                .build();

        EducationAbsoluteTimeline.StageInterval highInterval = new EducationAbsoluteTimeline.StageInterval(
                EducationStage.HIGH,
                YearMonth.of(2026, 4),
                YearMonth.of(2029, 3),
                new BigDecimal("30000") // 30k/month living cost
        );

        EducationAbsoluteTimeline timeline = EducationAbsoluteTimeline.builder()
                .events(List.of(entranceFee, term1, term2))
                .stageIntervals(Map.of(EducationStage.HIGH, highInterval))
                .build();

        // When simulation starts in September 2026 (fromMonth = 9):
        // Remaining events: only 2026-10 Tuition (500,000). (4月已繳的80萬不計入)
        // Living cost: Sep, Oct, Nov, Dec = 4 active months * 30,000 = 120,000.
        // Total expected = 500,000 + 120,000 = 620,000.
        BigDecimal remaining2026 = calculator.calculateCostForRemainingYear(2026, 9, timeline);
        assertThat(remaining2026).isEqualByComparingTo("620000");

        // When precomputing annual cost map with transition year (2026, 9):
        Map<Integer, Long> costMap = calculator.buildAnnualCostMap(timeline, 2026, 9);
        assertThat(costMap.get(2026)).isEqualTo(620000L);
    }

    @Test
    @DisplayName("Should accurately calculate annual cost and remaining cost with monthly living cost based on active months")
    void shouldCalculateAnnualCostAndRemainingCostWithMonthlyLivingCost() {
        // University interval: 2026-09 ~ 2030-06, monthly living cost = 125,000
        // Discrete event: 2026-09 Tuition 1,000,000
        EducationPaymentEvent tuition2026 = EducationPaymentEvent.builder()
                .paymentMonth(YearMonth.of(2026, 9))
                .amount(new BigDecimal("1000000"))
                .type(PaymentType.TUITION)
                .stage(EducationStage.UNIVERSITY)
                .build();

        EducationAbsoluteTimeline.StageInterval uniInterval = new EducationAbsoluteTimeline.StageInterval(
                EducationStage.UNIVERSITY,
                YearMonth.of(2026, 9),
                YearMonth.of(2030, 6),
                new BigDecimal("125000") // 125,000 / month
        );

        EducationAbsoluteTimeline timeline = EducationAbsoluteTimeline.builder()
                .events(List.of(tuition2026))
                .stageIntervals(Map.of(EducationStage.UNIVERSITY, uniInterval))
                .build();

        // 1. Year 2026 Annual Cost:
        // Tuition: 1,000,000
        // Living cost: Sep, Oct, Nov, Dec = 4 active months * 125,000 = 500,000
        // Total expected = 1,500,000
        BigDecimal annualCost2026 = calculator.calculateAnnualCost(2026, timeline);
        assertThat(annualCost2026).isEqualByComparingTo("1500000");

        // 2. Year 2027 Annual Cost (Full year active, no events):
        // Living cost: 12 active months * 125,000 = 1,500,000
        BigDecimal annualCost2027 = calculator.calculateAnnualCost(2027, timeline);
        assertThat(annualCost2027).isEqualByComparingTo("1500000");

        // 3. Year 2030 Annual Cost (Graduation year, Jan ~ Jun = 6 active months):
        // Living cost: 6 active months * 125,000 = 750,000
        BigDecimal annualCost2030 = calculator.calculateAnnualCost(2030, timeline);
        assertThat(annualCost2030).isEqualByComparingTo("750000");

        // 4. Remaining cost in 2026 starting from November (fromMonth = 11):
        // Past event: 2026-09 Tuition excluded
        // Remaining active months in [11, 12]: 2 months (Nov, Dec) * 125,000 = 250,000
        BigDecimal remainingCost2026 = calculator.calculateCostForRemainingYear(2026, 11, timeline);
        assertThat(remainingCost2026).isEqualByComparingTo("250000");
    }
}
