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

/**
 * Exception thrown when child education settings produce an invalid timeline,
 * such as overlapping stage dates, negative intervals, or out-of-boundary
 * parameters.
 */
public class InvalidEducationTimelineException extends RuntimeException {

    public InvalidEducationTimelineException(String message) {
        super(message);
    }

    public InvalidEducationTimelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
