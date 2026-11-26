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
package ch.lin.finance.hub.backend.api.domain.model.event;

import java.time.YearMonth;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = LifeEvent.TABLE_NAME)
@Getter
@EqualsAndHashCode(of = { "startYearMonth", "eventName" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LifeEvent extends MultiUserBaseEntity {
    public static final String TABLE_NAME = "simulation_life_event";

    // ownerUserId is inherited from FinanceBaseEntity

    @NotNull
    @Column(name = "start_year_month", nullable = false)
    private java.time.YearMonth startYearMonth;

    @NotNull
    @Column(name = "end_year_month", nullable = false)
    private java.time.YearMonth endYearMonth;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private LifeEventFrequency frequency;

    @NotNull
    @Column(name = "event_name", nullable = false)
    private String eventName;

    @NotNull
    @Column(name = "cost_per_occurrence", nullable = false)
    private Long costPerOccurrence;

    public LifeEvent(YearMonth startYearMonth, YearMonth endYearMonth, LifeEventFrequency frequency, String eventName,
            Long costPerOccurrence) {
        this.startYearMonth = startYearMonth;
        this.endYearMonth = endYearMonth;
        this.frequency = frequency;
        this.eventName = eventName;
        this.costPerOccurrence = costPerOccurrence != null ? costPerOccurrence : 0L;
        validateDates();
    }

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    private void validateDates() {
        if (startYearMonth != null && endYearMonth != null && endYearMonth.isBefore(startYearMonth)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }
}
