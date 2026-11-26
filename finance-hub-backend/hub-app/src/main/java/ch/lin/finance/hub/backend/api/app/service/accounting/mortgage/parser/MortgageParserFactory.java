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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.parser;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class MortgageParserFactory {

    private final List<MortgageParser> parsers;

    public MortgageParserFactory(List<MortgageParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * Finds a parser capable of parsing the given file.
     * 
     * @param file the HTML or CSV file to parse
     * @return the MortgageParser capable of parsing the file
     * @throws IOException              if an error occurs while reading the file
     * @throws IllegalArgumentException if no parser is found
     */
    public MortgageParser getParser(File file) throws IOException, IllegalArgumentException {
        for (MortgageParser parser : parsers) {
            if (parser.canParse(file)) {
                return parser;
            }
        }
        throw new IllegalArgumentException("No suitable parser found for file: " + file.getName());
    }
}
