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

import java.io.File;

/**
 * Defines the contract for parsing credit card statement files.
 * Implementations are typically tailored to specific banks or card issuers.
 */
public interface CreditCardParser {

    /**
     * Determines if this parser knows how to parse the given file based on its
     * preview.
     *
     * @param preview A lightweight preview containing the first line of the file.
     * @return {@code true} if this parser can handle the file, otherwise
     *         {@code false}.
     */
    boolean canParse(FilePreview preview);

    /**
     * Parses the CSV file into a result containing structured transactions and any
     * failed rows.
     *
     * @param file The file to parse.
     * @return The parsing result encapsulated in a {@link CreditCardParseResult}.
     * @throws Exception If an unrecoverable error occurs during file reading.
     */
    CreditCardParseResult parse(File file) throws Exception;
}
