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
package ch.lin.finance.hub.backend.api.app.service.accounting.insurance;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class InsuranceParserFactory {

    private final List<InsuranceParser> parsers;

    public InsuranceParserFactory(List<InsuranceParser> parsers) {
        this.parsers = parsers;
    }

    public InsuranceParser getParser(InsuranceFilePreview preview) {
        for (InsuranceParser parser : parsers) {
            if (parser.canParse(preview)) {
                return parser;
            }
        }
        throw new IllegalArgumentException("No suitable insurance parser found for the provided file preview");
    }
}
