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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.service.report.strategy.TransactionReportStrategy;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Implementation of {@link ReportService}.
 * <p>
 * This class orchestrates the generation of comprehensive markdown financial
 * reports.
 * It iterates over supported {@link TransactionSource}s, delegates to
 * respective
 * {@link TransactionReportStrategy} instances, and compiles the sections into a
 * single report.
 */
@Service
public class ReportServiceImpl implements ReportService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(ReportServiceImpl.class);

    /**
     * Map of supported transaction sources to their respective reporting
     * strategies.
     */
    private final Map<TransactionSource, TransactionReportStrategy> strategyMap;

    /**
     * Constructs the ReportServiceImpl with a list of available strategies.
     *
     * @param strategies A list of {@link TransactionReportStrategy} implementations
     *                   provided by Spring.
     */
    public ReportServiceImpl(List<TransactionReportStrategy> strategies) {
        // Convert the List into a Map keyed by TransactionSource for O(1) strategy
        // lookup
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(strategy -> strategy.getSupportedSource(), strategy -> strategy));
        logger.info("Initialized ReportServiceImpl with {} report strategies", strategyMap.size());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Generates a composite markdown report by invoking the appropriate strategy
     * for each
     * requested transaction source, accumulating the total amount, and appending
     * the sections.
     */
    @Override
    @Transactional(readOnly = true)
    public String generateSummaryReport(GenerateReportCommand command) {
        ReportDateRange dateRange = command.resolveDateRange();
        logger.info("Starting summary report generation for period: {} to {}", dateRange.startDate(),
                dateRange.endDate());

        Set<TransactionSource> resolvedSources = new java.util.HashSet<>();
        Set<ReportType> types = command.getTypes();
        if (types == null || types.isEmpty()) {
            resolvedSources.addAll(ReportType.ALL.getSources());
        } else {
            types.stream()
                    .flatMap(type -> type.getSources().stream())
                    .forEach(resolvedSources::add);
        }

        BigDecimal grandTotal = BigDecimal.ZERO;
        StringBuilder sectionsBuilder = new StringBuilder();

        for (TransactionSource source : TransactionSource.values()) {
            if (!resolvedSources.contains(source)) {
                logger.debug("Skipping source {} as it is not requested", source);
                continue;
            }

            TransactionReportStrategy strategy = strategyMap.get(source);
            if (strategy != null) {
                logger.info("Generating report section for source: {}", source);
                BigDecimal sectionTotal = strategy.generateReportSection(dateRange, command.getSortOption(),
                        command.getCurrentUserId(), sectionsBuilder);
                grandTotal = grandTotal.add(sectionTotal);
                logger.debug("Section total for {}: {}", source, sectionTotal);
            } else {
                logger.warn("No reporting strategy found for source: {}", source);
            }
        }

        logger.info("Finished generating all sections. Grand total: {}", grandTotal);

        StringBuilder sb = new StringBuilder();
        sb.append("# Financial Summary Report: ").append(command.getReportPeriodString()).append("\n\n");

        sb.append("## Grand Total Spending: **").append(grandTotal).append(" JPY**\n");
        if (resolvedSources.contains(TransactionSource.AMAZON)
                && resolvedSources.contains(TransactionSource.CREDIT_CARD)) {
            sb.append(
                    "> **Note:** If your Amazon orders were paid using the tracked credit cards, this grand total may contain double-counted amounts.\n\n");
        } else {
            sb.append("\n");
        }

        // Append all generated source sections to the bottom
        sb.append(sectionsBuilder);

        return sb.toString();
    }
}
