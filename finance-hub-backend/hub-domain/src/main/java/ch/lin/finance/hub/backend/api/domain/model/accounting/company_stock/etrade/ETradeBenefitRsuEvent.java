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
package ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade;

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

@Entity
@Table(name = ETradeBenefitRsuEvent.TABLE_NAME, indexes = {
        @Index(name = ETradeBenefitRsuEvent.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
                BaseETradeEntity.SYMBOL_COLUMN,
                ETradeBenefitRsuEvent.GRANT_NUMBER_COLUMN,
                ETradeBenefitRsuEvent.EVENT_DATE_COLUMN,
                ETradeBenefitRsuEvent.EVENT_TYPE_COLUMN,
                ETradeBenefitRsuEvent.QUANTITY_COLUMN
        })
})
@Getter
@EqualsAndHashCode(of = { "grantNumber", "eventDate", "eventType", "quantity" }, callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ETradeBenefitRsuEvent extends BaseETradeEntity {

    public static final String TABLE_NAME = "etrade_benefit_rsu_event";
    public static final String ID_INDEX = "etrade_benefit_rsu_event_id_idx";

    public static final String GRANT_NUMBER_COLUMN = "grant_number";
    public static final String EVENT_DATE_COLUMN = "event_date";
    public static final String EVENT_TYPE_COLUMN = "event_type";
    public static final String QUANTITY_COLUMN = "quantity";

    @NotNull
    @Column(name = GRANT_NUMBER_COLUMN, nullable = false, length = 50)
    private String grantNumber;

    @NotNull
    @Column(name = EVENT_DATE_COLUMN, nullable = false)
    private LocalDate eventDate;

    @NotNull
    @Column(name = EVENT_TYPE_COLUMN, nullable = false, length = 50)
    private String eventType;

    @ColumnDefault("0")
    @Column(name = QUANTITY_COLUMN)
    private Double quantity = 0.0;

    public ETradeBenefitRsuEvent(String symbol, String grantNumber, LocalDate eventDate, String eventType,
            Double quantity) {
        super(symbol);
        this.grantNumber = grantNumber;
        this.eventDate = eventDate;
        this.eventType = eventType;
        this.quantity = quantity != null ? quantity : 0.0;
    }

    public void merge(ETradeBenefitRsuEvent incoming) {
        if (incoming == null) {
            return;
        }
        this.grantNumber = incoming.grantNumber;
        this.eventDate = incoming.eventDate;
        this.eventType = incoming.eventType;
        this.quantity = incoming.quantity;
    }
}
