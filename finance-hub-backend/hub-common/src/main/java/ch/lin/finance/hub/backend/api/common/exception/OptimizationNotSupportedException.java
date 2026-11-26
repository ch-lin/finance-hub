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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an optimization configuration or strategy is
 * unsupported
 * (e.g., attempting mortgage search in GridSearch mode or exceeding search
 * space bounds).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class OptimizationNotSupportedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new OptimizationNotSupportedException with the specified detail
     * message.
     *
     * @param message the detail message.
     */
    public OptimizationNotSupportedException(String message) {
        super(message);
    }

    /**
     * Constructs a new OptimizationNotSupportedException with the specified detail
     * message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause.
     */
    public OptimizationNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
