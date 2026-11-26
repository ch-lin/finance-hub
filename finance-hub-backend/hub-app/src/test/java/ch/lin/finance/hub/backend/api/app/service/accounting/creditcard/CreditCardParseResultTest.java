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
package ch.lin.finance.hub.backend.api.app.service.accounting.creditcard;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;

class CreditCardParseResultTest {

    @Test
    void testConstructorAndGetters() {
        CreditCardStatement statement = new CreditCardStatement("Test", "2024-01", null, 0);
        List<String> failedRows = Arrays.asList("row1", "row2");

        CreditCardParseResult result = new CreditCardParseResult(statement, failedRows, StandardCharsets.UTF_8);

        assertSame(statement, result.getStatement());
        assertEquals(failedRows, result.getFailedRows());
        assertEquals(StandardCharsets.UTF_8, result.getCharset());
    }
}
