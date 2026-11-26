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
package ch.lin.finance.hub.backend.api.common.exception;

import java.io.Serial;

import java.util.List;

/**
 * Exception thrown when a CSV file contains rows that failed to parse correctly.
 */
public class CsvRowsFailedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<String> failedRows;

    /**
     * Constructs a new CsvRowsFailedException.
     *
     * @param message    The detail message.
     * @param failedRows The list of rows that failed to parse.
     */

    public CsvRowsFailedException(String message, List<String> failedRows) {
        super(message);
        this.failedRows = failedRows;
    }

    /**
     * Retrieves the list of rows that failed to parse.
     *
     * @return A list of failed row strings.
     */
    public List<String> getFailedRows() {
        return failedRows;
    }
}
