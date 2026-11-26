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
 * Represents a single parsed contribution setting for a NISA fund.
 */
@Entity
@Table(name = NisaContributionSetting.TABLE_NAME, indexes = {
        @Index(name = NisaContributionSetting.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                NisaContributionSetting.RECORD_DATE_COLUMN,
                NisaContributionSetting.ACCOUNT_TYPE_COLUMN, NisaContributionSetting.FUND_CODE_COLUMN,
                NisaContributionSetting.COURSE_COLUMN })
})
@Getter
@EqualsAndHashCode(of = { "recordDate", "accountType", "fundCode", "course" }, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NisaContributionSetting extends MultiUserBaseEntity {

    public static final String TABLE_NAME = "nisa_contribution_setting";
    public static final String ID_INDEX = "nisa_contribution_setting_id_index";
    public static final String RECORD_DATE_COLUMN = "record_date";
    public static final String ACCOUNT_TYPE_COLUMN = "account_type";
    public static final String FUND_CODE_COLUMN = "fund_code";
    public static final String FUND_NAME_COLUMN = "fund_name";
    public static final String COURSE_COLUMN = "course";

    /**
     * The date this setting was parsed/recorded.
     */
    @NotNull
    @Column(name = NisaContributionSetting.RECORD_DATE_COLUMN, nullable = false)
    private LocalDate recordDate;

    /**
     * The account type (e.g., NISA (成長), NISA (つみたて)).
     */
    @NotNull
    @Column(name = NisaContributionSetting.ACCOUNT_TYPE_COLUMN, nullable = false)
    private String accountType;

    /**
     * The official fund code (投信協会コード, e.g., "03311187", "0331418A").
     */
    @Column(name = NisaContributionSetting.FUND_CODE_COLUMN)
    private String fundCode;

    /**
     * The name of the investment fund.
     */
    @NotNull
    @Column(name = NisaContributionSetting.FUND_NAME_COLUMN, nullable = false)
    private String fundName;

    /**
     * The contribution course (e.g., 毎日, 毎月).
     */
    @NotNull
    @Column(name = NisaContributionSetting.COURSE_COLUMN, nullable = false)
    private String course;

    /**
     * The contribution amount per execution (e.g., 5000, 50000).
     */
    @ColumnDefault("0")
    @Column(name = "setting_amount")
    private Long settingAmount = 0L;

    /**
     * The expected annual return rate for this specific fund holding. Null
     * means it falls back to the global/user default.
     */
    @Column(name = "expected_return_rate")
    private Double expectedReturnRate;

    public NisaContributionSetting(LocalDate recordDate, String accountType, String fundCode, String fundName,
            String course,
            Long settingAmount) {
        this.recordDate = recordDate;
        this.accountType = accountType;
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.course = course;
        this.settingAmount = settingAmount != null ? settingAmount : 0L;
        this.expectedReturnRate = null;
    }

    public void updateExpectedReturnRate(Double expectedReturnRate) {
        this.expectedReturnRate = expectedReturnRate;
    }

    /**
     * Merges incoming contribution setting facts into this setting:
     * - Overwrites all parsed facts unconditionally using direct assignment.
     * - Strictly preserves user-defined fields (expectedReturnRate).
     *
     * @param incoming The parsed contribution setting containing updated facts.
     */
    public void merge(NisaContributionSetting incoming) {
        if (incoming == null) {
            return;
        }
        this.recordDate = incoming.getRecordDate();
        this.accountType = incoming.getAccountType();
        this.fundCode = incoming.getFundCode();
        this.fundName = incoming.getFundName();
        this.course = incoming.getCourse();
        this.settingAmount = incoming.getSettingAmount();
        // expectedReturnRate is user-defined and strictly preserved
    }
}
