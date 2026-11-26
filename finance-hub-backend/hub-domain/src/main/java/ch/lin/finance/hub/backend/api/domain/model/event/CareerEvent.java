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
@Table(name = CareerEvent.TABLE_NAME)
@Getter
@EqualsAndHashCode(of = { "eventYearMonth", "eventName" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareerEvent extends MultiUserBaseEntity {
    public static final String TABLE_NAME = "simulation_career_event";

    // ownerUserId is inherited from FinanceBaseEntity

    @NotNull
    @Column(name = "event_year_month", nullable = false)
    private YearMonth eventYearMonth;

    @NotNull
    @Column(name = "event_name", nullable = false)
    private String eventName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private CareerEventType eventType;

    @NotNull
    @Column(name = "event_value", nullable = false)
    private Double eventValue; // Absolute amount (e.g., 1000000) or relative (e.g., 1.1 for +10%)

    public CareerEvent(YearMonth eventYearMonth, String eventName, CareerEventType eventType, Double eventValue) {
        this.eventYearMonth = eventYearMonth;
        this.eventName = eventName;
        this.eventType = eventType;
        this.eventValue = eventValue != null ? eventValue : 0.0;
    }
}
