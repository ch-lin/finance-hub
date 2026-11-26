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
import java.util.List;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.common.exception.UnsupportedCsvFormatException;

/**
 * Factory class responsible for discovering the appropriate
 * {@link CreditCardParser}
 * for a given file by inspecting its contents using {@link FilePreview}.
 */
@Component
public class CreditCardParserFactory {

    private final List<CreditCardParser> parsers;

    /**
     * Constructs the factory with all available parser implementations.
     *
     * @param parsers List of injected {@link CreditCardParser} beans.
     */
    public CreditCardParserFactory(List<CreditCardParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * Inspects the given file and returns the first compatible parser.
     *
     * @param file The file to be parsed.
     * @return A compatible {@link CreditCardParser}.
     * @throws UnsupportedCsvFormatException If no suitable parser is found.
     */
    public CreditCardParser getParser(File file) {
        FilePreview preview = new FilePreview(file);
        return parsers.stream()
                .filter(parser -> parser.canParse(preview))
                .findFirst()
                .orElseThrow(() -> new UnsupportedCsvFormatException(
                        "No supported credit card parser found for file: " + file.getName()));
    }
}
