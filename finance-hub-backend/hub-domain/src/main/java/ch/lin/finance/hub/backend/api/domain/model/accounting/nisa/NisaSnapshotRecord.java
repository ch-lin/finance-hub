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
package ch.lin.finance.hub.backend.api.domain.model.accounting.nisa;

import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a single snapshot record of a NISA fund holding.
 */
@Entity
@Table(name = NisaSnapshotRecord.TABLE_NAME, indexes = {
        @Index(name = NisaSnapshotRecord.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                NisaSnapshotRecord.RECORD_DATE_COLUMN, NisaSnapshotRecord.ACCOUNT_TYPE_COLUMN,
                NisaSnapshotRecord.FUND_CODE_COLUMN })
})
@Getter
@EqualsAndHashCode(of = { "recordDate", "accountType", "fundCode" }, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NisaSnapshotRecord extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "nisa_snapshot_record";
    public static final String ID_INDEX = "nisa_snapshot_record_id_index";
    public static final String RECORD_DATE_COLUMN = "record_date";
    public static final String ACCOUNT_TYPE_COLUMN = "account_type";
    public static final String FUND_CODE_COLUMN = "fund_code";
    public static final String FUND_NAME_COLUMN = "fund_name";
    public static final String UNITS_COLUMN = "units";
    public static final String AVERAGE_COST_COLUMN = "average_cost";
    public static final String CURRENT_PRICE_COLUMN = "current_price";
    public static final String TOTAL_PRINCIPAL_COLUMN = "total_principal";
    public static final String CURRENT_MARKET_VALUE_COLUMN = "current_market_value";
    public static final String UNREALIZED_GAIN_LOSS_COLUMN = "unrealized_gain_loss";
    public static final String EXPECTED_RETURN_RATE_COLUMN = "expected_return_rate";

    /**
     * The date this snapshot was taken.
     */
    @NotNull
    @Column(name = NisaSnapshotRecord.RECORD_DATE_COLUMN, nullable = false)
    private LocalDate recordDate;

    /**
     * The type of NISA account (e.g., "NISA (成長)預り" or "NISA (つみたて)預り").
     */
    @NotNull
    @Column(name = NisaSnapshotRecord.ACCOUNT_TYPE_COLUMN, nullable = false)
    private String accountType; // e.g., "NISA (成長)預り" or "NISA (つみたて)預り"

    /**
     * The official fund code (投信協会コード, e.g., "03311187", "0331418A").
     */
    @Column(name = NisaSnapshotRecord.FUND_CODE_COLUMN)
    private String fundCode;

    /**
     * The name of the investment fund.
     */
    @NotNull
    @Column(name = NisaSnapshotRecord.FUND_NAME_COLUMN, nullable = false)
    private String fundName; // e.g., "ｅＭＡＸＩＳ Ｓｌｉｍ 米国株式（Ｓ＆Ｐ５００）"

    /**
     * The number of units held (保有口数).
     */
    @ColumnDefault("0")
    @Column(name = NisaSnapshotRecord.UNITS_COLUMN)
    private Double units = 0.0; // 保有口数

    /**
     * The average cost per unit (取得単価).
     */
    @ColumnDefault("0")
    @Column(name = NisaSnapshotRecord.AVERAGE_COST_COLUMN)
    private Double averageCost = 0.0; // 取得単価

    /**
     * The current price per unit (基準価額).
     */
    @ColumnDefault("0")
    @Column(name = NisaSnapshotRecord.CURRENT_PRICE_COLUMN)
    private Double currentPrice = 0.0; // 基準価額

    /**
     * The total principal invested (取得金額).
     */
    @ColumnDefault("0")
    @Column(name = NisaSnapshotRecord.TOTAL_PRINCIPAL_COLUMN)
    private Long totalPrincipal = 0L; // 取得金額

    /**
     * The current market value of the holdings (評価額).
     */
    @ColumnDefault("0")
    @Column(name = NisaSnapshotRecord.CURRENT_MARKET_VALUE_COLUMN)
    private Long currentMarketValue = 0L; // 評価額

    /**
     * The unrealized gain or loss (評価損益).
     */
    @ColumnDefault("0")
    @Column(name = NisaSnapshotRecord.UNREALIZED_GAIN_LOSS_COLUMN)
    private Long unrealizedGainLoss = 0L; // 評価損益

    /**
     * The expected return rate for this specific fund holding.
     */
    @Column(name = NisaSnapshotRecord.EXPECTED_RETURN_RATE_COLUMN)
    private Double expectedReturnRate; // 預期年化報酬率

    /**
     * Constructs a snapshot record.
     *
     * <p>
     * This constructor enforces that all parsed factual fields are provided at
     * creation time. User-managed fields such as expectedReturnRate default to null
     * and are updated via dedicated domain methods or preserved across re-imports.
     */
    public NisaSnapshotRecord(LocalDate recordDate, String accountType, String fundCode, String fundName, Double units,
            Double averageCost, Double currentPrice, Long totalPrincipal, Long currentMarketValue,
            Long unrealizedGainLoss) {
        this.recordDate = recordDate;
        this.accountType = accountType;
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.units = units != null ? units : 0.0;
        this.averageCost = averageCost != null ? averageCost : 0.0;
        this.currentPrice = currentPrice != null ? currentPrice : 0.0;
        this.totalPrincipal = totalPrincipal != null ? totalPrincipal : 0L;
        this.currentMarketValue = currentMarketValue != null ? currentMarketValue : 0L;
        this.unrealizedGainLoss = unrealizedGainLoss != null ? unrealizedGainLoss : 0L;
        this.expectedReturnRate = null; // Can be set later via business method
    }

    /**
     * Updates the expected return rate for this snapshot.
     *
     * @param expectedReturnRate The new expected return rate.
     */
    public void updateExpectedReturnRate(Double expectedReturnRate) {
        this.expectedReturnRate = expectedReturnRate;
    }

    /**
     * Merges incoming snapshot record facts into this record:
     * - Overwrites all parsed holding facts unconditionally using direct
     * assignment.
     * - Strictly preserves user-defined fields (expectedReturnRate).
     *
     * @param incoming The parsed snapshot record containing updated facts.
     */
    public void merge(NisaSnapshotRecord incoming) {
        if (incoming == null) {
            return;
        }
        this.recordDate = incoming.getRecordDate();
        this.accountType = incoming.getAccountType();
        this.fundCode = incoming.getFundCode();
        this.fundName = incoming.getFundName();
        this.units = incoming.getUnits();
        this.averageCost = incoming.getAverageCost();
        this.currentPrice = incoming.getCurrentPrice();
        this.totalPrincipal = incoming.getTotalPrincipal();
        this.currentMarketValue = incoming.getCurrentMarketValue();
        this.unrealizedGainLoss = incoming.getUnrealizedGainLoss();
        // expectedReturnRate is user-defined and strictly preserved
    }
}
