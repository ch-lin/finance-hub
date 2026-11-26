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
package ch.lin.finance.hub.backend.api.app.service.report;

import java.util.Set;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import lombok.Getter;

/**
 * Enumeration representing predefined report combinations or individual
 * sources.
 * Maps high-level report categories to sets of specific
 * {@link TransactionSource}s.
 */
@Getter
public enum ReportType {
    // Group types
    ALL(TransactionSource.AMAZON, TransactionSource.CREDIT_CARD, TransactionSource.RAKUTEN),
    SHOPPING(TransactionSource.AMAZON, TransactionSource.RAKUTEN),
    // Individual types
    CREDIT_CARD(TransactionSource.CREDIT_CARD),
    AMAZON(TransactionSource.AMAZON),
    RAKUTEN(TransactionSource.RAKUTEN);

    private final Set<TransactionSource> sources;

    /**
     * Constructs a ReportType with the given transaction sources.
     *
     * @param sources The transaction sources associated with this report type.
     */
    ReportType(TransactionSource... sources) {
        this.sources = Set.of(sources);
    }
}
