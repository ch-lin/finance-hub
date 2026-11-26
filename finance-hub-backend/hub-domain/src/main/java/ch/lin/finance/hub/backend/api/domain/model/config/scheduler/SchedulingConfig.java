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
package ch.lin.finance.hub.backend.api.domain.model.config.scheduler;

import org.hibernate.annotations.ColumnDefault;

import static ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig.TABLE_NAME;
import ch.lin.platform.domain.model.AuditableEntity;
import ch.lin.platform.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the configuration for scheduling a specific transaction source or
 * data import job.
 */
@Entity
@Table(name = TABLE_NAME, indexes = {
        @Index(name = SchedulingConfig.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
}, uniqueConstraints = {
        @UniqueConstraint(name = SchedulingConfig.UNIQUE_NAME, columnNames = {
                SchedulingConfig.TRANSACTION_SOURCE_COLUMN,
                SchedulingConfig.NAME_COLUMN })
})
@Getter
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation. However, from a Domain-Driven Design (DDD) perspective, an
// entity should never be created in an invalid state without its required
// fields. Therefore, we use AccessLevel.PROTECTED to satisfy JPA/Hibernate
// requirements while preventing arbitrary instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {
        "name", "transactionSource", "enabled", "autoStartImportScheduler",
        "schedulerType", "fixedRate", "cronExpression", "cronTimeZone"
}, callSuper = false)
public class SchedulingConfig extends AuditableEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "scheduling_config";

    public static final String ID_INDEX = "scheduling_config_id_index";
    public static final String UNIQUE_NAME = "uk_scheduling_config_name";

    /**
     * The name of the name column.
     */
    public static final String NAME_COLUMN = "name";

    /**
     * The name of the transaction source column.
     */
    public static final String TRANSACTION_SOURCE_COLUMN = "transaction_source";

    /**
     * The name of the enabled column.
     */
    public static final String ENABLED_COLUMN = "enabled";

    /**
     * The name of the auto start import scheduler column.
     */
    public static final String AUTO_START_IMPORT_SCHEDULER_COLUMN = "auto_start_import_scheduler";

    /**
     * The name of the scheduler type column.
     */
    public static final String SCHEDULER_TYPE_COLUMN = "scheduler_type";

    /**
     * The name of the fixed rate column.
     */
    public static final String FIXED_RATE_COLUMN = "fixed_rate";

    /**
     * The name of the cron expression column.
     */
    public static final String CRON_EXPRESSION_COLUMN = "cron_expression";

    /**
     * The name of the cron time zone column.
     */
    public static final String CRON_TIME_ZONE_COLUMN = "cron_time_zone";

    /**
     * The name of the configuration.
     */
    @NotNull
    @Column(name = SchedulingConfig.NAME_COLUMN, nullable = false)
    private String name;

    /**
     * The transaction source (e.g., AMAZON, RAKUTEN).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = SchedulingConfig.TRANSACTION_SOURCE_COLUMN, nullable = false)
    private TransactionSource transactionSource;

    /**
     * A flag to indicate whether this source configuration is active.
     */
    @NotNull
    @ColumnDefault("false")
    @Column(name = SchedulingConfig.ENABLED_COLUMN, nullable = false)
    @Setter
    private Boolean enabled = false;

    /**
     * A flag to indicate whether the import scheduler should start
     * automatically.
     */
    @ColumnDefault("false")
    @Column(name = SchedulingConfig.AUTO_START_IMPORT_SCHEDULER_COLUMN)
    @Setter
    private Boolean autoStartImportScheduler = false;

    /**
     * The type of scheduler to use.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'FIXED_RATE'")
    @Column(name = SchedulingConfig.SCHEDULER_TYPE_COLUMN, nullable = false)
    @Setter
    private SchedulerType schedulerType = SchedulerType.FIXED_RATE;

    /**
     * The fixed rate for the scheduler in milliseconds.
     */
    @ColumnDefault("3600000")
    @Column(name = SchedulingConfig.FIXED_RATE_COLUMN)
    @Setter
    private Long fixedRate = 3600000L;

    /**
     * The cron expression for the scheduler.
     */
    @Column(name = SchedulingConfig.CRON_EXPRESSION_COLUMN)
    @Setter
    private String cronExpression;

    /**
     * The time zone for the cron expression.
     */
    @Column(name = SchedulingConfig.CRON_TIME_ZONE_COLUMN)
    @Setter
    private String cronTimeZone;

    /**
     * Creates a new SchedulingConfig with the specified name and source.
     *
     * @param name              The unique name for this configuration.
     * @param transactionSource The transaction source.
     */
    public SchedulingConfig(String name, TransactionSource transactionSource) {
        this.name = name;
        this.transactionSource = transactionSource;
    }
}
