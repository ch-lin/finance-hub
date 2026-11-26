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
package ch.lin.finance.hub.backend.api.domain.model.config.education;

import java.util.Objects;

import ch.lin.platform.domain.model.AuditableEntity;
import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Root domain entity representing the complete education cost configuration.
 *
 * <p>Contains baseline configurations across all educational stages (Preschool,
 * Primary, Middle, High, University). Baseline values are loaded from configuration
 * properties (e.g., {@code application.properties} or environment variables).</p>
 *
 * <p><b>Important Unit Conventions:</b></p>
 * <ul>
 *   <li>All track tuitions represent <b>Annual Tuition</b> in JPY.</li>
 *   <li>All track durations represent total study duration strictly in <b>Months</b> (e.g., 36 or 72 months).</li>
 * </ul>
 */
@Entity
@Table(name = EducationConfig.TABLE_NAME, indexes = {
    @Index(name = EducationConfig.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
}, uniqueConstraints = {
    @UniqueConstraint(name = EducationConfig.UNIQUE_NAME, columnNames = EducationConfig.NAME_COLUMN)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"name"}, callSuper = false)
public class EducationConfig extends AuditableEntity {

    public static final String TABLE_NAME = "finance_education_config";
    public static final String ID_INDEX = "finance_education_config_id_index";
    public static final String UNIQUE_NAME = "uk_finance_education_config_name";
    public static final String NAME_COLUMN = "name";

    @Column(name = NAME_COLUMN, nullable = false, length = 100)
    private String name;

    /** Preschool stage configuration (Track tuitions are annual; track durations are in months, e.g., 36 months). */
    @Embedded
    private PreschoolConfig preschool;

    /** Primary school stage configuration (Track tuitions are annual; track durations are in months, e.g., 72 months). */
    @Embedded
    private PrimaryConfig primary;

    /** Middle school stage configuration (Track tuitions are annual; track durations are in months, e.g., 36 months). */
    @Embedded
    private MiddleConfig middle;

    /** High school stage configuration (Track tuitions are annual; track durations are in months, e.g., 36 months). */
    @Embedded
    private HighConfig high;

    /** University stage configuration (Track tuitions are annual; track durations are in months, e.g., 36–48 months; baseline living cost is monthly). */
    @Embedded
    private UniversityConfig university;

    @Builder
    public EducationConfig(String name, PreschoolConfig preschool, PrimaryConfig primary,
            MiddleConfig middle, HighConfig high, UniversityConfig university) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.preschool = Objects.requireNonNull(preschool, "preschool must not be null");
        this.primary = Objects.requireNonNull(primary, "primary must not be null");
        this.middle = Objects.requireNonNull(middle, "middle must not be null");
        this.high = Objects.requireNonNull(high, "high must not be null");
        this.university = Objects.requireNonNull(university, "university must not be null");
    }

    public void update(PreschoolConfig preschool, PrimaryConfig primary,
            MiddleConfig middle, HighConfig high, UniversityConfig university) {
        this.preschool = Objects.requireNonNull(preschool, "preschool must not be null");
        this.primary = Objects.requireNonNull(primary, "primary must not be null");
        this.middle = Objects.requireNonNull(middle, "middle must not be null");
        this.high = Objects.requireNonNull(high, "high must not be null");
        this.university = Objects.requireNonNull(university, "university must not be null");
    }
}
