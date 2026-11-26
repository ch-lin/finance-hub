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



/**
 * Defines the contract for parsing bank statement files.
 * Implementations are typically tailored to specific banks.
 */
public interface BankParser {

    /**
     * Determines if this parser knows how to parse the given file based on its
     * preview.
     *
     * @param preview A lightweight preview containing a snippet of the file.
     * @return {@code true} if this parser can handle the file, otherwise
     *         {@code false}.
     */
    boolean canParse(BankFilePreview preview);

    /**
     * Parses the file into a result containing structured transactions.
     *
     * @param preview The file preview containing the cached document.
     * @return The parsing result encapsulated in a {@link BankParseResult}.
     * @throws Exception If an unrecoverable error occurs during file reading.
     */
    BankParseResult parse(BankFilePreview preview) throws Exception;
}
