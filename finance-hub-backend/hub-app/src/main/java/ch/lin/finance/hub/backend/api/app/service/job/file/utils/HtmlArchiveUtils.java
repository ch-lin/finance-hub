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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling HTML receipts and their associated resource
 * folders
 * (e.g. Chrome's "Webpage, Complete" save format).
 */
public final class HtmlArchiveUtils {

    private static final Logger logger = LoggerFactory.getLogger(HtmlArchiveUtils.class);

    private HtmlArchiveUtils() {
        // Utility class
    }

    /**
     * Scans the input path for HTML files up to a specific depth, while explicitly
     * skipping common resource directories to prevent partial file parsing.
     *
     * @param importPath The root path to scan.
     * @return List of discovered HTML files.
     * @throws IOException If scanning fails.
     */
    public static List<File> findHtmlFiles(String importPath) throws IOException {
        File inputDirectory = new File(importPath);
        if (!inputDirectory.exists() || !inputDirectory.isDirectory()) {
            logger.warn("Input path invalid or does not exist: {}", importPath);
            return new ArrayList<>();
        }

        List<File> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(inputDirectory.toPath(), 5)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".html"))
                    .filter(p -> {
                        String pathStr = p.toString().toLowerCase();
                        return !pathStr.contains("_files") && !pathStr.contains("_data")
                                && !pathStr.contains(" files") && !pathStr.contains(" data")
                                && !pathStr.contains(" 檔案") && !pathStr.contains("_檔案");
                    })
                    .forEach(p -> files.add(p.toFile()));
        }
        return files;
    }

    /**
     * Extracts the year to be used for the archive folder.
     * Tries the parsed order date first, then the parent folder name, and finally
     * the current year.
     *
     * @param orderDate The parsed order date, may be null.
     * @param file      The original HTML file.
     * @return A string representing the year.
     */
    public static String extractYear(LocalDate orderDate, File file) {
        if (orderDate != null) {
            return String.valueOf(orderDate.getYear());
        }

        // Fallback: Check if parent folder is a year (4 digits)
        String parentName = file.getParentFile().getName();
        if (parentName.matches("\\d{4}")) {
            return parentName;
        }

        return String.valueOf(LocalDate.now().getYear());
    }

    /**
     * Moves a processed file (and its associated resource directory, if any)
     * to a specific sub-directory for archiving based on year.
     * 
     * @param file       The main HTML file to move.
     * @param importPath The base import path of the service.
     * @param subDir     The name of the target subdirectory (e.g.,
     *                   "archive/success").
     * @param year       The year folder to group the archive by.
     */
    public static void moveFileAndResources(File file, String importPath, String subDir, String year) {
        try {
            Path targetDirPath = Path.of(importPath, subDir, year);
            if (!Files.exists(targetDirPath)) {
                Files.createDirectories(targetDirPath);
            }

            // Move the main HTML file
            Path targetFilePath = targetDirPath.resolve(file.getName());
            Files.move(file.toPath(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);

            // Move associated resource folder (if saved as "Webpage, Complete")
            String baseName = file.getName().substring(0, file.getName().lastIndexOf('.'));
            String[] possibleSuffixes = { "_files", "_data", " files", " data", " 檔案", "_檔案" };
            for (String suffix : possibleSuffixes) {
                File resourceFolder = new File(file.getParent(), baseName + suffix);
                if (resourceFolder.exists() && resourceFolder.isDirectory()) {
                    Path targetResourcePath = targetDirPath.resolve(resourceFolder.getName());
                    Files.move(resourceFolder.toPath(), targetResourcePath, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Moved associated resource folder {} to {}", resourceFolder.getName(),
                            subDir + "/" + year);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to move file {} to sub-directory {}", file.getName(), subDir + "/" + year, e);
        }
    }

    /**
     * Recursively cleans up empty directories within the given directory.
     *
     * @param directory The root directory to start cleanup from.
     */
    public static void cleanupEmptyDirectories(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    cleanupEmptyDirectories(file);
                }
            }
        }

        // After checking children, check if this directory is now empty
        File[] remainingFiles = directory.listFiles();
        if (remainingFiles != null && remainingFiles.length == 0) {
            try {
                Files.delete(directory.toPath());
                logger.debug("Deleted empty directory: {}", directory.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("Failed to delete empty directory: {}. Reason: {}", directory.getAbsolutePath(),
                        e.getMessage());
            }
        }
    }
}
