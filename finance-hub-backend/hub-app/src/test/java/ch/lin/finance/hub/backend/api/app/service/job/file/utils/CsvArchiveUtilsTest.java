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
package ch.lin.finance.hub.backend.api.app.service.job.file.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvArchiveUtilsTest {

    @TempDir
    File tempDir;

    @Test
    void testMoveFile() throws IOException {
        File importDir = new File(tempDir, "creditcard");
        importDir.mkdirs();

        File sourceFile = new File(importDir, "statement.csv");
        sourceFile.createNewFile();

        String timestamp = "20250510123045";
        CsvArchiveUtils.moveFile(sourceFile, importDir.getAbsolutePath(), "archive/success", timestamp);

        File targetFile = new File(importDir, "archive/success/statement_" + timestamp + ".csv");
        assertTrue(targetFile.exists());
        assertFalse(sourceFile.exists());
    }

    @Test
    void testWriteErrorLog() throws IOException {
        File importDir = new File(tempDir, "creditcard");
        importDir.mkdirs();

        File originalFile = new File(importDir, "statement.csv");
        Files.writeString(originalFile.toPath(), "col1,col2");

        List<String> failedRows = Arrays.asList("row1,bad", "row2,worse");
        String timestamp = "20250510123045";

        CsvArchiveUtils.writeErrorLog(originalFile, importDir.getAbsolutePath(), "archive/failed", failedRows,
                timestamp, StandardCharsets.UTF_8);

        File logFile = new File(importDir, "archive/failed/statement_error_log_" + timestamp + ".csv");
        assertTrue(logFile.exists());

        List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
        assertEquals("col1,col2,\"Error_Reason\"", lines.get(0));
        assertEquals("row1,bad", lines.get(1));
        assertEquals("row2,worse", lines.get(2));
    }

    @Test
    void testCleanupEmptyDirectories() throws IOException {
        File rootDir = new File(tempDir, "source");
        rootDir.mkdirs();

        // 1. Empty dir
        File emptyDir = new File(rootDir, "empty");
        emptyDir.mkdirs();

        // 2. Dir with .DS_Store only
        File dsStoreDir = new File(rootDir, "ds_store_only");
        dsStoreDir.mkdirs();
        new File(dsStoreDir, ".DS_Store").createNewFile();

        // 3. Dir with actual file
        File validDir = new File(rootDir, "valid");
        validDir.mkdirs();
        new File(validDir, "data.csv").createNewFile();

        // 4. Nested empty dir
        File parentDir = new File(rootDir, "parent");
        File childDir = new File(parentDir, "child");
        childDir.mkdirs();

        CsvArchiveUtils.cleanupEmptyDirectories(rootDir);

        assertTrue(rootDir.exists(), "Root dir should not be deleted");
        assertTrue(validDir.exists(), "Dir with meaningful files should not be deleted");
        assertFalse(emptyDir.exists(), "Empty dir should be deleted");
        assertFalse(dsStoreDir.exists(), "Dir with only hidden files should be deleted");
        assertFalse(parentDir.exists(), "Nested empty dirs should be deleted from bottom up");
    }
}
