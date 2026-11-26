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
package ch.lin.finance.hub.backend.api.domain.model.history;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a historical record of an import job execution. Tracks whether the
 * job was triggered manually or by a scheduler, its current status, and a
 * summary of the processed files.
 */
@Entity
@Table(name = ImportJobHistory.TABLE_NAME, indexes = {
        @Index(name = ImportJobHistory.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
})
@Getter
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation. However, from a Domain-Driven Design (DDD) perspective, an
// entity should never be created in an invalid state without its required
// fields. Therefore, we use AccessLevel.PROTECTED to satisfy JPA/Hibernate
// requirements while preventing arbitrary instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {
        "transactionSource", "triggerType", "status", "startTime", "endTime",
        "totalFiles", "successCount", "failedCount", "needsReviewCount"
}, callSuper = false)
public class ImportJobHistory extends MultiUserBaseEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "import_job_history";

    public static final String ID_INDEX = "import_job_history_id_index";

    /**
     * The name of the transaction source column.
     */
    public static final String TRANSACTION_SOURCE_COLUMN = "transaction_source";

    /**
     * The name of the trigger type column.
     */
    public static final String TRIGGER_TYPE_COLUMN = "trigger_type";

    /**
     * The name of the status column.
     */
    public static final String STATUS_COLUMN = "status";

    /**
     * The name of the start time column.
     */
    public static final String START_TIME_COLUMN = "start_time";

    /**
     * The name of the end time column.
     */
    public static final String END_TIME_COLUMN = "end_time";

    /**
     * The name of the total files column.
     */
    public static final String TOTAL_FILES_COLUMN = "total_files";

    /**
     * The name of the success count column.
     */
    public static final String SUCCESS_COUNT_COLUMN = "success_count";

    /**
     * The name of the failed count column.
     */
    public static final String FAILED_COUNT_COLUMN = "failed_count";

    /**
     * The name of the needs review count column.
     */
    public static final String NEEDS_REVIEW_COUNT_COLUMN = "needs_review_count";

    /**
     * The name of the error message column.
     */
    public static final String ERROR_MESSAGE_COLUMN = "error_message";

    /**
     * The transaction source of the import job (e.g., AMAZON, RAKUTEN).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = ImportJobHistory.TRANSACTION_SOURCE_COLUMN, nullable = false)
    private TransactionSource transactionSource;

    /**
     * How the job was triggered (e.g., "MANUAL", "SCHEDULER").
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = ImportJobHistory.TRIGGER_TYPE_COLUMN, nullable = false)
    private TriggerType triggerType;

    /**
     * The current status of the job (e.g., "PROCESSING", "SUCCESS", "FAILED",
     * "PARTIAL_SUCCESS", "INTERRUPTED").
     */
    @NotNull
    @ColumnDefault("'PROCESSING'")
    @Column(name = ImportJobHistory.STATUS_COLUMN, nullable = false)
    @Setter
    private String status = "PROCESSING";

    /**
     * The start time of the import job.
     */
    @NotNull
    @Column(name = ImportJobHistory.START_TIME_COLUMN, nullable = false, columnDefinition = "TIMESTAMP")
    private OffsetDateTime startTime;

    /**
     * The end time of the import job.
     */
    @Column(name = ImportJobHistory.END_TIME_COLUMN, columnDefinition = "TIMESTAMP")
    @Setter
    private OffsetDateTime endTime;

    /**
     * The total number of files processed.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = ImportJobHistory.TOTAL_FILES_COLUMN, nullable = false)
    @Setter
    private Integer totalFiles = 0;

    /**
     * The number of successfully processed files.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = ImportJobHistory.SUCCESS_COUNT_COLUMN, nullable = false)
    @Setter
    private Integer successCount = 0;

    /**
     * The number of files that failed to process.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = ImportJobHistory.FAILED_COUNT_COLUMN, nullable = false)
    @Setter
    private Integer failedCount = 0;

    /**
     * The number of files that need review.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = ImportJobHistory.NEEDS_REVIEW_COUNT_COLUMN, nullable = false)
    @Setter
    private Integer needsReviewCount = 0;

    /**
     * Stores any high-level errors that caused the entire job to fail (e.g.,
     * "Input directory not found" or unexpected RuntimeExceptions).
     */
    @Column(name = ImportJobHistory.ERROR_MESSAGE_COLUMN, columnDefinition = "TEXT")
    @Setter
    private String errorMessage;

    /**
     * The list of files that failed during this job.
     */
    @OneToMany(mappedBy = "jobHistory", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ImportJobFailedFile> failedFiles = new ArrayList<>();

    /**
     * Helper method to add a failed file and keep both sides of the
     * bidirectional relationship in sync.
     *
     * @param failedFile The failed file to add.
     */
    public void addFailedFile(ImportJobFailedFile failedFile) {
        this.failedFiles.add(failedFile);
        failedFile.setJobHistory(this);
    }

    /**
     * Creates a new ImportJobHistory with the essential immutable fields.
     *
     * @param transactionSource The transaction source.
     * @param triggerType       How the job was triggered.
     * @param startTime         The start time of the import job.
     */
    public ImportJobHistory(TransactionSource transactionSource, TriggerType triggerType, OffsetDateTime startTime) {
        this.transactionSource = transactionSource;
        this.triggerType = triggerType;
        this.startTime = startTime;
    }
}
