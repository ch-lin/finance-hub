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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A lightweight utility that reads the first line of a file using common
 * encodings
 * (Shift-JIS/Windows-31J and UTF-8). It is used by parsers to quickly inspect
 * headers
 * and determine file format compatibility without loading the entire file into
 * memory.
 */
public class FilePreview {

    private static final Logger logger = LoggerFactory.getLogger(FilePreview.class);

    private final String headerWin31J;
    private final String headerUtf8;

    /**
     * Constructs a preview by eagerly reading the first line using multiple
     * encodings.
     *
     * @param file The file to preview.
     */
    public FilePreview(File file) {
        this.headerWin31J = readFirstLine(file, "Windows-31J");
        this.headerUtf8 = readFirstLine(file, "UTF-8");
    }

    /**
     * Reads the first line of the file using the specified encoding.
     *
     * @param file     The file to read.
     * @param encoding The character set encoding name.
     * @return The first line, or null if an error occurs.
     */
    private String readFirstLine(File file, String encoding) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding))) {
            return reader.readLine();
        } catch (Exception e) {
            logger.warn("Could not read file preview for {} with encoding {}", file.getName(), encoding);
            return null;
        }
    }

    /**
     * Gets the first line read with Windows-31J encoding.
     *
     * @return The first line, or null if reading failed.
     */
    public String getHeaderWin31J() {
        return headerWin31J;
    }

    /**
     * Gets the first line read with UTF-8 encoding.
     *
     * @return The first line, or null if reading failed.
     */
    public String getHeaderUtf8() {
        return headerUtf8;
    }
}
