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

import java.nio.charset.Charset;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;

/**
 * A wrapper class that holds the result of a credit card CSV file parsing
 * operation.
 * It contains the successfully parsed {@link CreditCardStatement}, a list of
 * any
 * rows that failed to parse, and the character set used to read the file.
 */
public class CreditCardParseResult {

    private final CreditCardStatement statement;
    private final List<String> failedRows;
    private final Charset charset;

    /**
     * Constructs a new parse result.
     *
     * @param statement  The structured statement parsed from the file.
     * @param failedRows A list of raw CSV strings that failed validation or
     *                   parsing.
     * @param charset    The character set used during parsing.
     */
    public CreditCardParseResult(CreditCardStatement statement, List<String> failedRows, Charset charset) {
        this.statement = statement;
        this.failedRows = failedRows;
        this.charset = charset;
    }

    /**
     * Gets the parsed credit card statement containing the transactions and
     * metadata.
     *
     * @return The parsed credit card statement.
     */
    public CreditCardStatement getStatement() {
        return statement;
    }

    /**
     * Gets the list of rows that failed to parse.
     *
     * @return A list of failed rows.
     */
    public List<String> getFailedRows() {
        return failedRows;
    }

    /**
     * Gets the charset encoding used during parsing.
     *
     * @return The charset used.
     */
    public Charset getCharset() {
        return charset;
    }
}
