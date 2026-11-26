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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.lin.finance.hub.backend.api.common.exception.UnsupportedCsvFormatException;

class CreditCardParserFactoryTest {

    @TempDir
    File tempDir;

    @Test
    void testGetParser_Success() throws IOException {
        File tempFile = new File(tempDir, "test.csv");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile),
                StandardCharsets.UTF_8)) {
            writer.write("Header1,Header2\nRow1,Row2");
        }

        CreditCardParser parser1 = mock(CreditCardParser.class);
        when(parser1.canParse(any(FilePreview.class))).thenReturn(false);

        CreditCardParser parser2 = mock(CreditCardParser.class);
        when(parser2.canParse(any(FilePreview.class))).thenReturn(true);

        CreditCardParserFactory factory = new CreditCardParserFactory(Arrays.asList(parser1, parser2));

        CreditCardParser result = factory.getParser(tempFile);

        assertEquals(parser2, result);
    }

    @Test
    void testGetParser_ThrowsUnsupportedCsvFormatException_WhenNoParserMatches() throws IOException {
        File tempFile = new File(tempDir, "test.csv");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile),
                StandardCharsets.UTF_8)) {
            writer.write("Header1,Header2\nRow1,Row2");
        }

        CreditCardParser parser1 = mock(CreditCardParser.class);
        when(parser1.canParse(any(FilePreview.class))).thenReturn(false);

        CreditCardParserFactory factory = new CreditCardParserFactory(Collections.singletonList(parser1));

        UnsupportedCsvFormatException exception = assertThrows(UnsupportedCsvFormatException.class,
                () -> factory.getParser(tempFile));
        assertNotNull(exception);
    }

    @Test
    void testGetParser_ThrowsUnsupportedCsvFormatException_WhenFileDoesNotExist() {
        File nonExistentFile = new File(tempDir, "does_not_exist.csv");

        CreditCardParser parser1 = mock(CreditCardParser.class);
        when(parser1.canParse(any(FilePreview.class))).thenReturn(false);

        CreditCardParserFactory factory = new CreditCardParserFactory(Collections.singletonList(parser1));

        UnsupportedCsvFormatException exception = assertThrows(UnsupportedCsvFormatException.class,
                () -> factory.getParser(nonExistentFile));
        assertNotNull(exception);
    }
}
