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
package ch.lin.finance.hub.backend.api.app.service.accounting.bank;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A lightweight utility that reads a snippet of a file to extract identifiers
 * and determine file format compatibility.
 */
public class BankFilePreview {

    private static final Logger logger = LoggerFactory.getLogger(BankFilePreview.class);

    private final File file;
    
    /**
     * Cached parsed HTML document if the file is an HTML format.
     */
    private Document htmlDocument;
    
    /**
     * Flag indicating whether an HTML parsing attempt has already been made.
     */
    private boolean htmlParsed = false;

    /**
     * Constructs a preview by storing the file reference.
     *
     * @param file The file to preview.
     */
    public BankFilePreview(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    /**
     * Reads a small snippet of the file using the specified encoding.
     *
     * @param file     The file to read.
     * @param encoding The character set encoding name.
     * @param length   The number of characters to read.
     * @return The snippet string, or null if an error occurs.
     */
    private String readSnippet(File file, String encoding, int length) {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), encoding)) {
            char[] buffer = new char[length];
            int numRead = reader.read(buffer, 0, buffer.length);
            return numRead != -1 ? new String(buffer, 0, numRead) : "";
        } catch (Exception e) {
            logger.warn("Could not read file preview for {} with encoding {}", file.getName(), encoding);
            return null;
        }
    }



    /**
     * Gets a preview snippet of the file content up to 64KB.
     * 
     * @param encoding The character encoding to use when reading
     * @return A string containing the snippet of the file
     */
    public String getContentSnippet(String encoding) {
        return readSnippet(file, encoding, 65536);
    }

    /**
     * Lazily parses the file as an HTML Document using Jsoup.
     * Caches the result to avoid parsing multiple times per file.
     *
     * @return The parsed Document, or null if it's not an HTML file or parsing failed.
     */
    public Document getHtmlDocument() {
        if (!htmlParsed) {
            htmlParsed = true;
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                try {
                    // null charset allows Jsoup to detect encoding from <meta> tags
                    htmlDocument = Jsoup.parse(file, null);
                } catch (java.io.IOException e) {
                    logger.warn("Failed to parse HTML document for preview: {}", file.getName(), e);
                }
            }
        }
        return htmlDocument;
    }
}
