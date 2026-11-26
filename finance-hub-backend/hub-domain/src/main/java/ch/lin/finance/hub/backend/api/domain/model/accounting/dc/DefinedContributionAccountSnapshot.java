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
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = DefinedContributionAccountSnapshot.TABLE_NAME, indexes = {
        @Index(name = DefinedContributionAccountSnapshot.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                DefinedContributionAccountSnapshot.RECORD_DATE_COLUMN,
                DefinedContributionAccountSnapshot.TYPE_COLUMN })
})
@Getter
@EqualsAndHashCode(of = { "recordDate", "type" }, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefinedContributionAccountSnapshot extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "dc_account_snapshot";
    public static final String ID_INDEX = "dc_account_snapshot_id_index";
    public static final String RECORD_DATE_COLUMN = "record_date";
    public static final String TYPE_COLUMN = "type";

    @NotNull
    @Column(name = RECORD_DATE_COLUMN, nullable = false)
    private LocalDate recordDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = TYPE_COLUMN, nullable = false)
    private DefinedContributionType type;

    @ColumnDefault("0")
    @Column(name = "total_asset_value")
    private long totalAssetValue = 0L;

    @ColumnDefault("0")
    @Column(name = "total_accumulated_contribution")
    private long totalAccumulatedContribution = 0L;

    @ColumnDefault("0")
    @Column(name = "monthly_employer_contribution")
    private long monthlyEmployerContribution = 0L;

    @ColumnDefault("0")
    @Column(name = "monthly_subscriber_contribution")
    private long monthlySubscriberContribution = 0L;

    @ColumnDefault("0")
    @Column(name = "monthly_total_contribution")
    private long monthlyTotalContribution = 0L;

    @Setter
    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;

    @Column(name = "remainder_fund_code")
    private String remainderFundCode;

    @SuppressWarnings("FieldMayBeFinal")
    @OneToMany(mappedBy = "accountSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DefinedContributionFundSnapshot> funds = new ArrayList<>();

    public DefinedContributionAccountSnapshot(LocalDate recordDate, DefinedContributionType type, long totalAssetValue,
            long totalAccumulatedContribution, long monthlyEmployerContribution,
            long monthlySubscriberContribution, long monthlyTotalContribution) {
        this.recordDate = recordDate;
        this.type = type;
        this.totalAssetValue = totalAssetValue;
        this.totalAccumulatedContribution = totalAccumulatedContribution;
        this.monthlyEmployerContribution = monthlyEmployerContribution;
        this.monthlySubscriberContribution = monthlySubscriberContribution;
        this.monthlyTotalContribution = monthlyTotalContribution;
    }

    public void addFund(DefinedContributionFundSnapshot fund) {
        funds.add(fund);
        fund.setAccountSnapshot(this);
    }

    public void updateRemainderFundCode(String remainderFundCode) {
        this.remainderFundCode = remainderFundCode;
    }

    /**
     * Merges factual fields from a MEMBER_INFO page facet.
     * Updates enrollment date without touching financial amounts.
     */
    public void mergeMemberInfoFacts(DefinedContributionAccountSnapshot incoming) {
        this.enrollmentDate = incoming.enrollmentDate;
    }

    /**
     * Merges factual fields from an ALLOCATION page facet.
     * Directly overwrites monthly contribution amounts and per-fund allocation
     * facts.
     * Keeps balance facts and remainder fund code intact.
     */
    public void mergeAllocationFacts(DefinedContributionAccountSnapshot incoming) {
        this.monthlyEmployerContribution = incoming.monthlyEmployerContribution;
        this.monthlySubscriberContribution = incoming.monthlySubscriberContribution;
        this.monthlyTotalContribution = incoming.monthlyTotalContribution;

        for (DefinedContributionFundSnapshot otherFund : incoming.funds) {
            DefinedContributionFundSnapshot existingFund = findFundByCode(otherFund.getFundCode());

            if (existingFund != null) {
                existingFund.mergeAllocationFacts(otherFund);
            } else {
                DefinedContributionFundSnapshot newFund = new DefinedContributionFundSnapshot(
                        otherFund.getCommodityType(),
                        otherFund.getFundName(),
                        otherFund.getFundCode(),
                        0.0,
                        0.0,
                        0.0,
                        0L,
                        0L,
                        0L,
                        otherFund.getMonthlyContributionAmount(),
                        otherFund.getAllocationPercentage());
                this.addFund(newFund);
            }
        }
    }

    /**
     * Merges factual fields from a STATUS page facet.
     * Directly overwrites total asset values and per-fund holding balance facts.
     * Keeps allocation facts intact.
     */
    public void mergeStatusFacts(DefinedContributionAccountSnapshot incoming) {
        this.totalAssetValue = incoming.totalAssetValue;
        this.totalAccumulatedContribution = incoming.totalAccumulatedContribution;

        for (DefinedContributionFundSnapshot otherFund : incoming.funds) {
            DefinedContributionFundSnapshot existingFund = findFundByCode(otherFund.getFundCode());

            if (existingFund != null) {
                existingFund.mergeStatusFacts(otherFund);
            } else {
                DefinedContributionFundSnapshot newFund = new DefinedContributionFundSnapshot(
                        otherFund.getCommodityType(),
                        otherFund.getFundName(),
                        otherFund.getFundCode(),
                        otherFund.getUnits(),
                        otherFund.getAverageCost(),
                        otherFund.getCurrentPrice(),
                        otherFund.getTotalPrincipal(),
                        otherFund.getCurrentMarketValue(),
                        otherFund.getUnrealizedGainLoss(),
                        0L,
                        0.0);
                this.addFund(newFund);
            }
        }
    }

    private DefinedContributionFundSnapshot findFundByCode(String fundCode) {
        return this.funds.stream()
                .filter(f -> fundCode.equals(f.getFundCode()))
                .findFirst()
                .orElse(null);
    }
}
