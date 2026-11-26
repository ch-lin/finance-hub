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


import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Factory class responsible for discovering the appropriate {@link BankParser}
 * for a given file by inspecting its contents using {@link BankFilePreview}.
 */
@Component
public class BankParserFactory {

    private final List<BankParser> parsers;

    /**
     * Constructs the factory with all available parser implementations.
     *
     * @param parsers List of injected {@link BankParser} beans.
     */
    public BankParserFactory(List<BankParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * Inspects the given preview and returns the first compatible parser.
     *
     * @param preview The file preview.
     * @return A compatible {@link BankParser}.
     * @throws IllegalArgumentException If no suitable parser is found.
     */
    public BankParser getParser(BankFilePreview preview) {
        return parsers.stream()
                .filter(parser -> parser.canParse(preview))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No supported bank parser found for file: " + preview.getFile().getName()));
    }
}
