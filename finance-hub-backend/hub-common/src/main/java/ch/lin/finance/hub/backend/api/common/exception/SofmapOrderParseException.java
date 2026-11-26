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

/**
 * Exception thrown when an error occurs during the parsing of a Sofmap order HTML file.
 */
public class SofmapOrderParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new SofmapOrderParseException.
     *
     * @param message The detail message.
     */
    public SofmapOrderParseException(String message) {
        super(message);
    }

    /**
     * Constructs a new SofmapOrderParseException.
     *
     * @param message The detail message.
     * @param cause   The cause of the exception.
     */
    public SofmapOrderParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
