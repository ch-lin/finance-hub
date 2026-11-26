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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;
import lombok.Getter;

/**
 * Encapsulates the internal result of an exchange rate file parsing operation.
 */
@Getter
public class ExchangeRateParseResult {

    private final List<ExchangeRateRecord> records;
    private final List<String> failedRows;

    public ExchangeRateParseResult(List<ExchangeRateRecord> records, List<String> failedRows) {
        this.records = records != null ? records : new ArrayList<>();
        this.failedRows = failedRows != null ? failedRows : new ArrayList<>();
    }

    public static ExchangeRateParseResult success(List<ExchangeRateRecord> records) {
        return new ExchangeRateParseResult(records, Collections.emptyList());
    }

    public static ExchangeRateParseResult empty() {
        return new ExchangeRateParseResult(Collections.emptyList(), Collections.emptyList());
    }
}
