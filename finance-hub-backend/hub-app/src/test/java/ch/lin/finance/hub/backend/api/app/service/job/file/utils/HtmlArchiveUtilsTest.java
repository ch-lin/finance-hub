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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlArchiveUtilsTest {

    @TempDir
    File tempDir;

    @Test
    void testFindHtmlFiles() throws IOException {
        File sourceDir = new File(tempDir, "source");
        sourceDir.mkdirs();

        // 1. Valid html file
        File validFile = new File(sourceDir, "order.html");
        validFile.createNewFile();

        // 2. Not html file
        File txtFile = new File(sourceDir, "order.txt");
        txtFile.createNewFile();

        // 3. Html file in a folder that should be excluded
        File excludedFolder = new File(sourceDir, "order_files");
        excludedFolder.mkdirs();
        File excludedHtml = new File(excludedFolder, "asset.html");
        excludedHtml.createNewFile();

        List<File> result = HtmlArchiveUtils.findHtmlFiles(sourceDir.getAbsolutePath());

        assertEquals(1, result.size());
        assertEquals(validFile.getAbsolutePath(), result.get(0).getAbsolutePath());
    }

    @Test
    void testFindHtmlFiles_WithAllExcludedSuffixes() throws IOException {
        File sourceDir = new File(tempDir, "source2");
        sourceDir.mkdirs();

        String[] excludedSuffixes = { "_data", " files", " data", " 檔案", "_檔案" };
        for (String suffix : excludedSuffixes) {
            File excludedFolder = new File(sourceDir, "order" + suffix);
            excludedFolder.mkdirs();
            File excludedHtml = new File(excludedFolder, "asset.html");
            excludedHtml.createNewFile();
        }

        List<File> result = HtmlArchiveUtils.findHtmlFiles(sourceDir.getAbsolutePath());
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindHtmlFiles_InvalidPath() throws IOException {
        List<File> result = HtmlArchiveUtils.findHtmlFiles(tempDir.getAbsolutePath() + "/non_existent");
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractYear_WithDate() {
        LocalDate date = LocalDate.of(2025, 5, 10);
        File dummyFile = new File(tempDir, "order.html");
        String year = HtmlArchiveUtils.extractYear(date, dummyFile);
        assertEquals("2025", year);
    }

    @Test
    void testExtractYear_FallbackToParent() throws IOException {
        File yearDir = new File(tempDir, "2024");
        yearDir.mkdirs();
        File dummyFile = new File(yearDir, "order.html");
        String year = HtmlArchiveUtils.extractYear(null, dummyFile);
        assertEquals("2024", year);
    }

    @Test
    void testExtractYear_FallbackToCurrent() throws IOException {
        File yearDir = new File(tempDir, "unknown");
        yearDir.mkdirs();
        File dummyFile = new File(yearDir, "order.html");
        String year = HtmlArchiveUtils.extractYear(null, dummyFile);
        assertEquals(String.valueOf(LocalDate.now().getYear()), year);
    }

    @Test
    void testMoveFileAndResources() throws IOException {
        File importPath = new File(tempDir, "amazon");
        File sourceDir = new File(importPath, "source");
        sourceDir.mkdirs();

        File htmlFile = new File(sourceDir, "order.html");
        htmlFile.createNewFile();

        File resourceFolder = new File(sourceDir, "order_files");
        resourceFolder.mkdirs();
        File resourceChild = new File(resourceFolder, "style.css");
        resourceChild.createNewFile();

        HtmlArchiveUtils.moveFileAndResources(htmlFile, importPath.getAbsolutePath(), "archive/success", "2025");

        File targetHtml = new File(importPath, "archive/success/2025/order.html");
        File targetResource = new File(importPath, "archive/success/2025/order_files");
        File targetResourceChild = new File(targetResource, "style.css");

        assertTrue(targetHtml.exists());
        assertTrue(targetResourceChild.exists());
        assertFalse(htmlFile.exists());
        assertFalse(resourceFolder.exists());
    }

    @Test
    void testMoveFileAndResources_TargetDirExists() throws IOException {
        File importPath = new File(tempDir, "amazon_exists");
        File sourceDir = new File(importPath, "source");
        sourceDir.mkdirs();

        File htmlFile = new File(sourceDir, "order.html");
        htmlFile.createNewFile();

        // Pre-create the target directory
        File targetDir = new File(importPath, "archive/success/2025");
        targetDir.mkdirs();

        HtmlArchiveUtils.moveFileAndResources(htmlFile, importPath.getAbsolutePath(), "archive/success", "2025");

        File targetHtml = new File(targetDir, "order.html");
        assertTrue(targetHtml.exists());
    }

    @Test
    void testMoveFileAndResources_IOException() throws IOException {
        File importPath = new File(tempDir, "amazon_io");
        File sourceDir = new File(importPath, "source");
        sourceDir.mkdirs();

        File htmlFile = new File(sourceDir, "order.html");
        htmlFile.createNewFile();

        File targetDir = new File(importPath, "archive/success/2025");
        targetDir.mkdirs();
        // Make the target directory read-only so that Files.move throws an IOException
        targetDir.setWritable(false);

        try {
            HtmlArchiveUtils.moveFileAndResources(htmlFile, importPath.getAbsolutePath(), "archive/success", "2025");
            // The method catches the exception and logs an error, so it should not throw.
            // But the file should not be moved.
            assertTrue(htmlFile.exists());
        } finally {
            targetDir.setWritable(true);
        }
    }
}
