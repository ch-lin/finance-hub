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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling CSV receipts/statements archiving and error
 * logging.
 */
public final class CsvArchiveUtils {

    private static final Logger logger = LoggerFactory.getLogger(CsvArchiveUtils.class);

    private CsvArchiveUtils() {
        // Utility class
    }

    /**
     * Moves a file to an archive sub-directory and appends a timestamp to its name.
     *
     * @param file       The file to move.
     * @param importPath The base import path.
     * @param subDir     The sub-directory path under the import directory.
     * @param timestamp  The timestamp to append to the filename.
     * @throws IOException If the file operation fails.
     */
    public static void moveFile(File file, String importPath, String subDir, String timestamp) throws IOException {
        Path targetDirPath = Path.of(importPath, subDir);
        if (!Files.exists(targetDirPath)) {
            Files.createDirectories(targetDirPath);
        }

        // Extract original name and extension to append a timestamp
        String originalName = file.getName();
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
            originalName = originalName.substring(0, dotIndex);
        }

        String newFileName = originalName + "_" + timestamp + extension;

        Path targetFilePath = targetDirPath.resolve(newFileName);
        Files.move(file.toPath(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Generates a separate CSV error log containing the original header and the
     * rows that failed to parse, appending an "Error_Reason" column for context.
     *
     * @param originalFile The original source file.
     * @param importPath   The base import path.
     * @param subDir       The target sub-directory.
     * @param failedRows   List of raw CSV rows that failed parsing.
     * @param timestamp    Timestamp string for unique naming.
     * @param charset      The detected charset to ensure proper encoding of the
     *                     output.
     * @throws IOException If writing the log fails.
     */
    public static void writeErrorLog(File originalFile, String importPath, String subDir, List<String> failedRows,
            String timestamp, Charset charset) throws IOException {
        Path targetDirPath = Path.of(importPath, subDir);
        if (!Files.exists(targetDirPath)) {
            Files.createDirectories(targetDirPath);
        }

        String originalName = originalFile.getName();
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            originalName = originalName.substring(0, dotIndex);
        }

        String errorFileName = originalName + "_error_log_" + timestamp + ".csv";
        Path errorFilePath = targetDirPath.resolve(errorFileName);

        // Fallback to UTF-8 if the parser didn't provide a Charset
        if (charset == null) {
            charset = java.nio.charset.StandardCharsets.UTF_8;
        }

        // Read the first line from the original file to use as the header
        String header;
        try (BufferedReader reader = Files.newBufferedReader(originalFile.toPath(), charset)) {
            header = reader.readLine();
        }

        // Prepare the content to write by prepending the header
        List<String> contentToWrite = new ArrayList<>();
        if (header != null) {
            contentToWrite.add(header + ",\"Error_Reason\""); // Append Error_Reason column to the header
        }
        contentToWrite.addAll(failedRows);

        // Write the error log with the same encoding to avoid garbled text in Excel
        Files.write(errorFilePath, contentToWrite, charset);
    }

    /**
     * Cleans up empty directories left behind after moving files, ensuring
     * a tidy folder structure in the source import directory.
     *
     * @param sourceDirectory The root directory to scan for empty subfolders.
     */
    public static void cleanupEmptyDirectories(File sourceDirectory) {
        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            paths.filter(Files::isDirectory)
                    .filter(p -> !p.equals(sourceDirectory.toPath())) // Do not delete the root source directory itself
                    // Sort by path depth in reverse order (deepest first to ensure subdirectories
                    // are removed before their parents)
                    .sorted((p1, p2) -> Integer.compare(p2.getNameCount(), p1.getNameCount()))
                    .forEach(dirPath -> {
                        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                            boolean hasMeaningfulFiles = false;
                            List<Path> allEntries = new ArrayList<>();
                            for (Path entry : stream) {
                                allEntries.add(entry);
                                File file = entry.toFile();
                                // We just check startsWith(".") to ignore macOS .DS_Store and similar files,
                                // avoiding impossible branch combinations on Unix with file.isHidden().
                                if (!file.getName().startsWith(".")) {
                                    hasMeaningfulFiles = true;
                                }
                            }

                            if (!hasMeaningfulFiles) {
                                for (Path entry : allEntries) {
                                    Files.deleteIfExists(entry);
                                }
                                Files.delete(dirPath);
                                logger.info("Deleted empty directory (ignored hidden files): {}", dirPath);
                            }
                        } catch (IOException e) {
                            logger.warn("Could not check or delete empty directory: {}", dirPath, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error during empty directory cleanup", e);
        }
    }
}
