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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmazonOrderParseExceptionTest {
    @Test
    void testConstructors() {
        AmazonOrderParseException ex1 = new AmazonOrderParseException("Error1");
        assertEquals("Error1", ex1.getMessage());

        Throwable cause = new RuntimeException("Cause");
        AmazonOrderParseException ex2 = new AmazonOrderParseException("Error2", cause);
        assertEquals("Error2", ex2.getMessage());
        assertEquals(cause, ex2.getCause());
    }
}
