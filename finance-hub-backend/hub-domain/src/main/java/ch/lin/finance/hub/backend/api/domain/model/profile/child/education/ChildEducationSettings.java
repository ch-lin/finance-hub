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
package ch.lin.finance.hub.backend.api.domain.model.profile.child.education;

import java.math.BigDecimal;
import java.util.Objects;

import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
import ch.lin.platform.domain.model.AuditableEntity;
import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Child education settings entity representing an individual child's education
 * tracks and overrides.
 *
 * <p>
 * <b>Important Unit Conventions for Overrides:</b>
 * </p>
 * <ul>
 * <li>Stage override {@code manualTuition} is <b>Annual Tuition</b> in JPY
 * (e.g., 1,500,000 JPY/year).</li>
 * <li>Stage override {@code manualDuration} is strictly in <b>Months</b> (e.g.,
 * 36 months; NOT in years).</li>
 * <li>{@code manualLivingCost} is <b>Monthly Living Cost</b> in JPY (converted
 * from user annual inputs by dividing by 12 on the client before
 * persisting).</li>
 * </ul>
 */
@Entity
@Table(name = ChildEducationSettings.TABLE_NAME, indexes = {
    @Index(name = ChildEducationSettings.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
}, uniqueConstraints = {
    @UniqueConstraint(name = ChildEducationSettings.UNIQUE_CHILD_PROFILE_ID, columnNames = ChildEducationSettings.CHILD_PROFILE_ID_COLUMN)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"childProfileId"}, callSuper = false)
public class ChildEducationSettings extends AuditableEntity {

    public static final String TABLE_NAME = "finance_child_education_settings";
    public static final String ID_INDEX = "finance_child_education_settings_id_idx";
    public static final String UNIQUE_CHILD_PROFILE_ID = "uk_finance_child_edu_settings_profile_id";
    public static final String CHILD_PROFILE_ID_COLUMN = "child_profile_id";

    @Column(name = CHILD_PROFILE_ID_COLUMN, nullable = false)
    private Long childProfileId;

    // --- Preschool ---
    /**
     * Preschool track selection (PreschoolTrack).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preschool_track")
    private PreschoolTrack preschoolTrack = PreschoolTrack.HOME;

    /**
     * Preschool custom override (manualTuition is annual tuition;
     * manualDuration is duration in months).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "manualTuition", column = @Column(name = "preschool_manual_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "manualDuration", column = @Column(name = "preschool_manual_duration")),
        @AttributeOverride(name = "manualStartMonth", column = @Column(name = "preschool_manual_start_month")),
        @AttributeOverride(name = "manualTermsPerYear", column = @Column(name = "preschool_manual_terms_per_year"))
    })
    private StageOverride preschoolOverride;

    /**
     * Whether preschool is split into two distinct stages (e.g. HOME first,
     * then INTL/DAYCARE).
     */
    @Column(name = "preschool_split_enabled")
    private Boolean preschoolSplitEnabled = false;

    /**
     * Duration of stage 1 in months (strictly 12 or 24 months; stage 2 fills
     * the remainder to 36 months).
     */
    @Column(name = "preschool_stage1_duration")
    private Integer preschoolStage1Duration;

    /**
     * Preschool track selection for stage 2 (PreschoolTrack: HOME, DAYCARE,
     * INTL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preschool_track2")
    private PreschoolTrack preschoolTrack2;

    /**
     * Preschool custom override for stage 2 (manualTuition is annual tuition;
     * manualDuration is duration in months).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "manualTuition", column = @Column(name = "preschool_manual_tuition2", precision = 19, scale = 4)),
        @AttributeOverride(name = "manualDuration", column = @Column(name = "preschool_manual_duration2")),
        @AttributeOverride(name = "manualStartMonth", column = @Column(name = "preschool_manual_start_month2")),
        @AttributeOverride(name = "manualTermsPerYear", column = @Column(name = "preschool_manual_terms_per_year2"))
    })
    private StageOverride preschoolOverride2;

    // --- Primary ---
    /**
     * Primary school track selection (PrimaryTrack).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_track")
    private PrimaryTrack primaryTrack = PrimaryTrack.PUBLIC;

    /**
     * Primary school custom override (manualTuition is annual tuition;
     * manualDuration is duration in months, e.g., 72).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "manualTuition", column = @Column(name = "primary_manual_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "manualDuration", column = @Column(name = "primary_manual_duration")),
        @AttributeOverride(name = "manualStartMonth", column = @Column(name = "primary_manual_start_month")),
        @AttributeOverride(name = "manualTermsPerYear", column = @Column(name = "primary_manual_terms_per_year"))
    })
    private StageOverride primaryOverride;

    // --- Middle ---
    /**
     * Middle school track selection (MiddleTrack).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "middle_track")
    private MiddleTrack middleTrack = MiddleTrack.PUBLIC;

    /**
     * Middle school custom override (manualTuition is annual tuition;
     * manualDuration is duration in months, e.g., 36).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "manualTuition", column = @Column(name = "middle_manual_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "manualDuration", column = @Column(name = "middle_manual_duration")),
        @AttributeOverride(name = "manualStartMonth", column = @Column(name = "middle_manual_start_month")),
        @AttributeOverride(name = "manualTermsPerYear", column = @Column(name = "middle_manual_terms_per_year"))
    })
    private StageOverride middleOverride;

    // --- High ---
    /**
     * High school track selection (HighTrack).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "high_track")
    private HighTrack highTrack = HighTrack.NONE;

    /**
     * High school custom override (manualTuition is annual tuition;
     * manualDuration is duration in months, e.g., 36).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "manualTuition", column = @Column(name = "high_manual_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "manualDuration", column = @Column(name = "high_manual_duration")),
        @AttributeOverride(name = "manualStartMonth", column = @Column(name = "high_manual_start_month")),
        @AttributeOverride(name = "manualTermsPerYear", column = @Column(name = "high_manual_terms_per_year"))
    })
    private StageOverride highOverride;

    // --- University ---
    /**
     * University track selection (UniversityTrack).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "university_track")
    private UniversityTrack universityTrack = UniversityTrack.NONE;

    /**
     * University custom override (manualTuition is annual tuition;
     * manualDuration is duration in months, e.g., 36 or 48).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "manualTuition", column = @Column(name = "uni_manual_tuition", precision = 19, scale = 4)),
        @AttributeOverride(name = "manualDuration", column = @Column(name = "uni_manual_duration")),
        @AttributeOverride(name = "manualStartMonth", column = @Column(name = "uni_manual_start_month")),
        @AttributeOverride(name = "manualTermsPerYear", column = @Column(name = "uni_manual_terms_per_year"))
    })
    private StageOverride universityOverride;

    /**
     * University custom MONTHLY living cost in JPY when studying abroad or
     * living away from home. (Under Issue 110 specification, the frontend
     * converts annual user input to monthly before submission).
     */
    @Column(name = "uni_manual_living_cost", precision = 19, scale = 4)
    private BigDecimal manualLivingCost;

    /**
     * Whether the student lives away from home or studies abroad during
     * university.
     */
    @Column(name = "is_away_from_home")
    private Boolean isAwayFromHome = false;

    public ChildEducationSettings(Long childProfileId) {
        this.childProfileId = Objects.requireNonNull(childProfileId, "childProfileId must not be null");
    }

    public ChildEducationSettings(Long childProfileId,
            PreschoolTrack preschoolTrack, StageOverride preschoolOverride,
            PrimaryTrack primaryTrack, StageOverride primaryOverride,
            MiddleTrack middleTrack, StageOverride middleOverride,
            HighTrack highTrack, StageOverride highOverride,
            UniversityTrack universityTrack, StageOverride universityOverride, BigDecimal manualLivingCost,
            Boolean isAwayFromHome) {
        this(childProfileId, preschoolTrack, preschoolOverride, false, null, null, null,
                primaryTrack, primaryOverride, middleTrack, middleOverride,
                highTrack, highOverride, universityTrack, universityOverride,
                manualLivingCost, isAwayFromHome);
    }

    @Builder
    public ChildEducationSettings(Long childProfileId,
            PreschoolTrack preschoolTrack, StageOverride preschoolOverride,
            Boolean preschoolSplitEnabled, Integer preschoolStage1Duration,
            PreschoolTrack preschoolTrack2, StageOverride preschoolOverride2,
            PrimaryTrack primaryTrack, StageOverride primaryOverride,
            MiddleTrack middleTrack, StageOverride middleOverride,
            HighTrack highTrack, StageOverride highOverride,
            UniversityTrack universityTrack, StageOverride universityOverride, BigDecimal manualLivingCost,
            Boolean isAwayFromHome) {
        this.childProfileId = Objects.requireNonNull(childProfileId, "childProfileId must not be null");
        this.preschoolTrack = preschoolTrack != null ? preschoolTrack : PreschoolTrack.HOME;
        this.preschoolOverride = preschoolOverride;
        this.preschoolSplitEnabled = preschoolSplitEnabled != null ? preschoolSplitEnabled : false;
        this.preschoolStage1Duration = preschoolStage1Duration;
        this.preschoolTrack2 = preschoolTrack2;
        this.preschoolOverride2 = preschoolOverride2;
        this.primaryTrack = primaryTrack != null ? primaryTrack : PrimaryTrack.PUBLIC;
        this.primaryOverride = primaryOverride;
        this.middleTrack = middleTrack != null ? middleTrack : MiddleTrack.PUBLIC;
        this.middleOverride = middleOverride;
        this.highTrack = highTrack != null ? highTrack : HighTrack.NONE;
        this.highOverride = highOverride;
        this.universityTrack = universityTrack != null ? universityTrack : UniversityTrack.NONE;
        this.universityOverride = universityOverride;
        this.manualLivingCost = manualLivingCost;
        this.isAwayFromHome = isAwayFromHome != null ? isAwayFromHome : false;
    }
}
