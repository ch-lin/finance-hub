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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

public interface ETradeParser<T> {

    /**
     * Checks if this parser can handle the given file based on its content or
     * structure.
     * 
     * @param file The file to check.
     * @return true if the parser can process it, false otherwise.
     * @throws IOException If an error occurs reading the file.
     */
    boolean canParse(File file) throws IOException;

    /**
     * Parses the E-Trade file into a result object.
     * 
     * @param file       The file to parse.
     * @param recordDate The fallback or explicit record date to use.
     * @return The parsed result.
     * @throws IOException              If an error occurs during parsing.
     * @throws IllegalArgumentException If the file format is invalid for this
     *                                  parser.
     */
    T parse(File file, LocalDate recordDate) throws IOException, IllegalArgumentException;

}
