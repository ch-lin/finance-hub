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

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Custom exception to encapsulate errors occurring during task execution or
 * file processing for a specific transaction source.
 */
public class TaskProcessingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TransactionSource transactionSource;
    private final String errorCode;

    /**
     * Constructs a new TaskProcessingException.
     *
     * @param transactionSource The transaction source that caused the error.
     * @param errorCode         The specific error code indicating the cause.
     * @param message           The detail message.
     * @param cause             The cause of the exception.
     */
    public TaskProcessingException(TransactionSource transactionSource, String errorCode, String message,
            Throwable cause) {
        super(message, cause);
        this.transactionSource = transactionSource;
        this.errorCode = errorCode;
    }

    /**
     * Retrieves the transaction source associated with the error.
     *
     * @return The transaction source.
     */
    public TransactionSource getTransactionSource() {
        return transactionSource;
    }

    /**
     * Retrieves the specific error code associated with the exception.
     *
     * @return The error code string.
     */
    public String getErrorCode() {
        return errorCode;
    }
}
