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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePreviewTest {

    @TempDir
    File tempDir;

    @Test
    void testFilePreview_WithValidFile() throws IOException {
        File tempFile = new File(tempDir, "test.csv");
        String content = "Header1,Header2\nRow1,Row2";

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile),
                StandardCharsets.UTF_8)) {
            writer.write(content);
        }

        FilePreview preview = new FilePreview(tempFile);

        assertEquals("Header1,Header2", preview.getHeaderUtf8());
        // Since it's standard ASCII characters, Windows-31J should read it the same way
        assertEquals("Header1,Header2", preview.getHeaderWin31J());
    }

    @Test
    void testFilePreview_WithWin31JEncoding() throws IOException {
        File tempFile = new File(tempDir, "test_win31j.csv");
        // Create a string with Japanese characters
        String content = "ヘッダー1,ヘッダー2\nデータ1,データ2";
        Charset win31j = Charset.forName("Windows-31J");

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), win31j)) {
            writer.write(content);
        }

        FilePreview preview = new FilePreview(tempFile);

        assertEquals("ヘッダー1,ヘッダー2", preview.getHeaderWin31J());
        // UTF-8 will likely garble it or return a different string, so we don't assert
        // it strictly,
        // but we verify that the Win31J header is correctly extracted.
    }

    @Test
    void testFilePreview_WithNonExistentFile() {
        File nonExistentFile = new File(tempDir, "does_not_exist.csv");

        FilePreview preview = new FilePreview(nonExistentFile);

        assertNull(preview.getHeaderUtf8());
        assertNull(preview.getHeaderWin31J());
    }
}
