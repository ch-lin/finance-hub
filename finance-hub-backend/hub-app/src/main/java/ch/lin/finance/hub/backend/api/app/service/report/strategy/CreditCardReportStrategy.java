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
package ch.lin.finance.hub.backend.api.app.service.report.strategy;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.service.report.ReportDateRange;
import ch.lin.finance.hub.backend.api.app.service.report.ReportSortOption;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Report strategy implementation for generating credit card spending summaries.
 * Evaluates {@link CreditCardTransaction} records and compiles a markdown
 * section
 * highlighting total spending, spending per card, and detailed transaction
 * logs.
 */
@Component
public class CreditCardReportStrategy implements TransactionReportStrategy {

    private final CreditCardTransactionRepository repository;

    /**
     * Constructs the CreditCardReportStrategy with the required repository.
     *
     * @param repository The repository for accessing credit card transactions.
     */
    public CreditCardReportStrategy(CreditCardTransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionSource getSupportedSource() {
        return TransactionSource.CREDIT_CARD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BigDecimal generateReportSection(ReportDateRange dateRange, ReportSortOption sortOption,
            String currentUserId,
            StringBuilder sectionBuilder) {
        List<CreditCardTransaction> transactions = repository.findByTransactionDateBetween(dateRange.startDate(),
                dateRange.endDate());

        Comparator<CreditCardTransaction> comparator = switch (sortOption) {
            case DATE_ASC -> Comparator.comparing(tx -> tx.getTransactionDate());
            case DATE_DESC -> Comparator.comparing((CreditCardTransaction tx) -> tx.getTransactionDate()).reversed();
            case AMOUNT_ASC -> Comparator.comparing(tx -> tx.getAmount());
            case AMOUNT_DESC -> Comparator.comparing((CreditCardTransaction tx) -> tx.getAmount()).reversed();
        };
        transactions.sort(comparator);

        sectionBuilder.append("## Credit Card Summary\n");
        if (transactions.isEmpty()) {
            sectionBuilder.append("No credit card transactions found for this period.\n\n");
            return BigDecimal.ZERO;
        }

        int totalAmount = transactions.stream().mapToInt(tx -> tx.getAmount()).sum();
        sectionBuilder.append("- **Total Credit Card Spending:** ").append(totalAmount).append(" JPY\n\n");

        Map<String, Integer> spendingByCard = transactions.stream()
                .collect(Collectors.groupingBy(tx -> tx.getCardName(), Collectors.summingInt(tx -> tx.getAmount())));

        sectionBuilder.append("### Spending by Card\n");
        spendingByCard.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sectionBuilder.append("- **").append(entry.getKey()).append(":** ")
                        .append(entry.getValue()).append(" JPY\n"));

        sectionBuilder.append("\n### Transaction Details\n");
        sectionBuilder.append("| Date | Card | Store | Amount |\n");
        sectionBuilder.append("|---|---|---|---|\n");
        for (CreditCardTransaction tx : transactions) {
            String storeName = tx.getStoreName();
            if (PrivacyMaskingUtil.shouldMask(currentUserId, tx.getOwnerUserId(), tx.getPrivacyLevel())) {
                storeName = PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.CREDIT_CARD);
            }
            sectionBuilder.append(String.format("| %s | %s | %s | %d JPY |\n", tx.getTransactionDate(),
                    tx.getCardName(), storeName, tx.getAmount()));
        }
        sectionBuilder.append("\n");
        return BigDecimal.valueOf(totalAmount);
    }
}
