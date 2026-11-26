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

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single file that failed to process during an import job
 * execution.
 */
@Entity
@Table(name = ImportJobFailedFile.TABLE_NAME, indexes = {
        @Index(name = ImportJobFailedFile.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN),
        @Index(name = ImportJobFailedFile.JOB_HISTORY_ID_INDEX, columnList = ImportJobFailedFile.JOB_HISTORY_ID_COLUMN)
})
@Getter
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation. However, from a Domain-Driven Design (DDD) perspective, an
// entity should never be created in an invalid state without its required
// fields. Therefore, we use AccessLevel.PROTECTED to satisfy JPA/Hibernate
// requirements while preventing arbitrary instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = { "filename", "errorMessage" }, callSuper = false)
public class ImportJobFailedFile extends MultiUserBaseEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "import_job_failed_file";

    public static final String ID_INDEX = "import_job_failed_file_id_index";
    public static final String JOB_HISTORY_ID_INDEX = "import_job_failed_file_job_history_id_index";

    /**
     * The name of the job history ID column.
     */
    public static final String JOB_HISTORY_ID_COLUMN = "job_history_id";

    /**
     * The name of the foreign key constraint for the job history.
     */
    public static final String FK_FAILED_FILE_JOB = "fk_failed_file_job";

    /**
     * The name of the filename column.
     */
    public static final String FILENAME_COLUMN = "filename";

    /**
     * The name of the error message column.
     */
    public static final String ERROR_MESSAGE_COLUMN = "error_message";

    /**
     * The name of the file that failed.
     */
    @NotNull
    @Column(name = ImportJobFailedFile.FILENAME_COLUMN, nullable = false)
    private String filename;

    /**
     * The error message indicating why the file failed to process.
     */
    @Column(name = ImportJobFailedFile.ERROR_MESSAGE_COLUMN, columnDefinition = "TEXT")
    @Setter
    private String errorMessage;

    /**
     * The associated import job history.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = JOB_HISTORY_ID_COLUMN, nullable = false, foreignKey = @ForeignKey(name = FK_FAILED_FILE_JOB))
    @Setter(AccessLevel.PACKAGE)
    private ImportJobHistory jobHistory;

    /**
     * Creates a new ImportJobFailedFile with the essential fields.
     *
     * @param filename   The name of the file that failed.
     * @param jobHistory The associated import job history.
     */
    public ImportJobFailedFile(String filename, ImportJobHistory jobHistory) {
        this.filename = filename;
        this.jobHistory = jobHistory;
    }
}
