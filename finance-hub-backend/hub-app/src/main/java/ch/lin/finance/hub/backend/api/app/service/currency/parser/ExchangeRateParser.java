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
package ch.lin.finance.hub.backend.api.app.service.currency.parser;

import java.io.File;

/**
 * Defines the contract for parsing exchange rate statement files (e.g. MURC Excel, Bank CSVs).
 */
public interface ExchangeRateParser {

    /**
     * Determines if this parser knows how to parse the given file based on its name or content.
     *
     * @param file The file to inspect.
     * @return {@code true} if this parser can handle the file; otherwise {@code false}.
     */
    boolean canParse(File file);

    /**
     * Parses the exchange rate file into structured records.
     *
     * @param file The file to parse.
     * @return An {@link ExchangeRateParseResult} containing parsed records and any failed rows.
     * @throws Exception If an unrecoverable error occurs during parsing.
     */
    ExchangeRateParseResult parse(File file) throws Exception;
}
