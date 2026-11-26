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
package ch.lin.finance.hub.backend.api.domain.model.accounting.dc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefinedContributionAccountSnapshotTest {

    @Test
    @DisplayName("mergeMemberInfoFacts: should update enrollmentDate without affecting financial values")
    void testMergeMemberInfoFacts() {
        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                1_000_000L,
                800_000L,
                0L,
                20_000L,
                20_000L);

        DefinedContributionAccountSnapshot memberInfo = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                0L, 0L, 0L, 0L, 0L);
        LocalDate enrollmentDate = LocalDate.of(2020, 5, 1);
        memberInfo.setEnrollmentDate(enrollmentDate);

        existing.mergeMemberInfoFacts(memberInfo);

        assertThat(existing.getEnrollmentDate()).isEqualTo(enrollmentDate);
        assertThat(existing.getTotalAssetValue()).isEqualTo(1_000_000L);
        assertThat(existing.getMonthlySubscriberContribution()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("mergeAllocationFacts: should unconditionally overwrite contributions even when 0, without touching balance")
    void testMergeAllocationFacts_UnconditionalZeroOverwrites() {
        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.CORPORATE_DC,
                2_500_000L,
                1_800_000L,
                10_000L,
                23_000L,
                33_000L);

        DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                "海外股票", "三菱UFJ純金ファンド", "9200181027",
                100.0, 15000.0, 16000.0, 1_500_000L, 1_600_000L, 100_000L,
                23_000L, 100.0);
        existing.addFund(fund);

        // Incoming allocation page facts: contribution stopped (becomes 0)
        DefinedContributionAccountSnapshot incomingAllocation = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.CORPORATE_DC,
                0L, 0L, 0L,
                0L, 0L);
        DefinedContributionFundSnapshot incomingFund = new DefinedContributionFundSnapshot(
                "海外股票", "三菱UFJ純金ファンド", "9200181027",
                0.0, 0.0, 0.0, 0L, 0L, 0L,
                0L, 0.0);
        incomingAllocation.addFund(incomingFund);

        existing.mergeAllocationFacts(incomingAllocation);

        // Account level contributions must be updated to 0
        assertThat(existing.getMonthlyEmployerContribution()).isEqualTo(0L);
        assertThat(existing.getMonthlySubscriberContribution()).isEqualTo(0L);
        assertThat(existing.getMonthlyTotalContribution()).isEqualTo(0L);

        // Existing balance values must be completely preserved
        assertThat(existing.getTotalAssetValue()).isEqualTo(2_500_000L);
        assertThat(existing.getTotalAccumulatedContribution()).isEqualTo(1_800_000L);

        // Fund level allocation must be updated to 0
        DefinedContributionFundSnapshot mergedFund = existing.getFunds().get(0);
        assertThat(mergedFund.getMonthlyContributionAmount()).isEqualTo(0L);
        assertThat(mergedFund.getAllocationPercentage()).isEqualTo(0.0);

        // Fund level balance values must remain untouched
        assertThat(mergedFund.getUnits()).isEqualTo(100.0);
        assertThat(mergedFund.getCurrentMarketValue()).isEqualTo(1_600_000L);
    }

    @Test
    @DisplayName("mergeStatusFacts: should unconditionally overwrite balance even when 0, without touching allocation")
    void testMergeStatusFacts_UnconditionalZeroOverwrites() {
        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.CORPORATE_DC,
                1_000_000L,
                500_000L,
                10_000L,
                10_000L,
                20_000L);

        DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                "外国株式", "インデックスファンド海外株式", "009911102C",
                50.0, 20000.0, 20000.0, 1_000_000L, 1_000_000L, 0L,
                20_000L, 100.0);
        existing.addFund(fund);

        // Incoming status page facts: balance reset or new holdings
        DefinedContributionAccountSnapshot incomingStatus = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.CORPORATE_DC,
                0L, 0L, 0L, 0L, 0L);
        DefinedContributionFundSnapshot incomingFund = new DefinedContributionFundSnapshot(
                "外国株式", "インデックスファンド海外株式", "009911102C",
                0.0, 0.0, 0.0, 0L, 0L, 0L,
                0L, 0.0);
        incomingStatus.addFund(incomingFund);

        existing.mergeStatusFacts(incomingStatus);

        // Asset balances must be updated to 0
        assertThat(existing.getTotalAssetValue()).isEqualTo(0L);
        assertThat(existing.getTotalAccumulatedContribution()).isEqualTo(0L);

        // Contribution settings must be preserved
        assertThat(existing.getMonthlyEmployerContribution()).isEqualTo(10_000L);
        assertThat(existing.getMonthlySubscriberContribution()).isEqualTo(10_000L);
        assertThat(existing.getMonthlyTotalContribution()).isEqualTo(20_000L);

        // Fund holding units must be updated to 0, but allocation percentage remains
        // 100.0
        DefinedContributionFundSnapshot mergedFund = existing.getFunds().get(0);
        assertThat(mergedFund.getUnits()).isEqualTo(0.0);
        assertThat(mergedFund.getMonthlyContributionAmount()).isEqualTo(20_000L);
        assertThat(mergedFund.getAllocationPercentage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("fund matching: should correctly match by fundCode even when fundName has slight variations")
    void testFundMatchingByFundCode() {
        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                0L, 0L, 0L, 20_000L, 20_000L);

        // Existing fund from allocation page with formal name
        DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                "海外株式", "三菱ＵＦＪ純金ファンド（愛称：ファインゴールド）", "9200181027",
                0.0, 0.0, 0.0, 0L, 0L, 0L,
                20_000L, 100.0);
        existing.addFund(fund);

        // Status page incoming facts with abbreviated name, but identical fundCode
        DefinedContributionAccountSnapshot statusSnapshot = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                500_000L, 400_000L, 0L, 0L, 0L);
        DefinedContributionFundSnapshot statusFund = new DefinedContributionFundSnapshot(
                "海外株式", "三菱UFJ純金ﾌｧﾝﾄﾞ", "9200181027",
                30.0, 15000.0, 16666.0, 450_000L, 500_000L, 50_000L,
                0L, 0.0);
        statusSnapshot.addFund(statusFund);

        existing.mergeStatusFacts(statusSnapshot);

        // Should NOT create duplicate funds
        assertThat(existing.getFunds()).hasSize(1);

        DefinedContributionFundSnapshot merged = existing.getFunds().get(0);
        assertThat(merged.getFundCode()).isEqualTo("9200181027");
        assertThat(merged.getUnits()).isEqualTo(30.0);
        assertThat(merged.getCurrentMarketValue()).isEqualTo(500_000L);
        assertThat(merged.getMonthlyContributionAmount()).isEqualTo(20_000L);
        assertThat(merged.getAllocationPercentage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("fund matching: should add new fund when fundCode does not exist")
    void testFundMatching_NewFundAdded() {
        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                500_000L, 400_000L, 0L, 0L, 0L);

        DefinedContributionFundSnapshot fund1 = new DefinedContributionFundSnapshot(
                "海外株式", "三菱UFJ純金ファンド", "9200181027",
                30.0, 15000.0, 16666.0, 450_000L, 500_000L, 50_000L,
                0L, 0.0);
        existing.addFund(fund1);

        DefinedContributionAccountSnapshot allocationSnapshot = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                0L, 0L, 0L, 30_000L, 30_000L);
        DefinedContributionFundSnapshot fund2 = new DefinedContributionFundSnapshot(
                "海外株式", "ＤＣニッセイ外国株式インデックス", "9200194887",
                0.0, 0.0, 0.0, 0L, 0L, 0L,
                30_000L, 100.0);
        allocationSnapshot.addFund(fund2);

        existing.mergeAllocationFacts(allocationSnapshot);

        assertThat(existing.getFunds()).hasSize(2);
        assertThat(existing.getFunds()).extracting(c -> c.getFundCode()).containsExactlyInAnyOrder("9200181027",
                "9200194887");
    }

    @Test
    @DisplayName("mergeAllocationFacts: should correctly update monthlyEmployerContribution from incoming allocation")
    void testMergeAllocationFacts_OverwritesEmployerContribution() {
        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.CORPORATE_DC,
                1_000_000L,
                500_000L,
                0L,
                0L,
                0L);

        DefinedContributionAccountSnapshot incoming = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.CORPORATE_DC,
                0L,
                0L,
                30_000L,
                10_000L,
                40_000L);

        existing.mergeAllocationFacts(incoming);

        assertThat(existing.getMonthlyEmployerContribution()).isEqualTo(30_000L);
        assertThat(existing.getMonthlySubscriberContribution()).isEqualTo(10_000L);
        assertThat(existing.getMonthlyTotalContribution()).isEqualTo(40_000L);
        assertThat(existing.getTotalAssetValue()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("updateRemainderFundCode: should set and preserve remainderFundCode across allocation/status merges")
    void testRemainderFundCodePreservedAcrossMerges() {
        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                1_000_000L,
                800_000L,
                0L,
                23_000L,
                23_000L);
        existing.updateRemainderFundCode("9200181027");

        DefinedContributionAccountSnapshot incomingAllocation = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                0L, 0L, 0L, 23_000L, 23_000L);
        existing.mergeAllocationFacts(incomingAllocation);

        DefinedContributionAccountSnapshot incomingStatus = new DefinedContributionAccountSnapshot(
                LocalDate.of(2025, 1, 1),
                DefinedContributionType.IDECO,
                1_100_000L, 823_000L, 0L, 0L, 0L);
        existing.mergeStatusFacts(incomingStatus);

        assertThat(existing.getRemainderFundCode()).isEqualTo("9200181027");
    }
}
