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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.RakutenOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.report.ReportDateRange;
import ch.lin.finance.hub.backend.api.app.service.report.ReportSortOption;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

/**
 * Report strategy implementation for generating Rakuten shopping summaries.
 * Evaluates {@link RakutenOrder} records and compiles a markdown section
 * highlighting
 * total billed amounts, savings, order summaries, and itemized details.
 */
@Component
public class RakutenReportStrategy implements TransactionReportStrategy {

    private final RakutenOrderRepository repository;

    /**
     * Constructs the RakutenReportStrategy with the required repository.
     *
     * @param repository The repository for accessing Rakuten orders.
     */
    public RakutenReportStrategy(RakutenOrderRepository repository) {
        this.repository = repository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionSource getSupportedSource() {
        return TransactionSource.RAKUTEN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BigDecimal generateReportSection(ReportDateRange dateRange, ReportSortOption sortOption,
            String currentUserId,
            StringBuilder sectionBuilder) {
        List<RakutenOrder> rawOrders = repository.findByOrderDateBetween(dateRange.startDate(), dateRange.endDate());
        List<RakutenOrder> orders = new ArrayList<>(rawOrders);

        Comparator<RakutenOrder> comparator = switch (sortOption) {
            case DATE_ASC -> Comparator.comparing(tx -> tx.getOrderDate());
            case DATE_DESC -> Comparator.comparing((RakutenOrder tx) -> tx.getOrderDate()).reversed();
            case AMOUNT_ASC -> Comparator.comparing(tx -> tx.getTotalAmount());
            case AMOUNT_DESC -> Comparator.comparing((RakutenOrder tx) -> tx.getTotalAmount()).reversed();
        };
        orders.sort(comparator);

        sectionBuilder.append("## Rakuten Shopping Summary\n");
        if (orders.isEmpty()) {
            sectionBuilder.append("No Rakuten orders found for this period.\n\n");
            return BigDecimal.ZERO;
        }

        BigDecimal totalBilled = orders.stream()
                .map(order -> order.getBilledAmount() != null ? order.getBilledAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        BigDecimal totalSaved = orders.stream()
                .map(order -> {
                    BigDecimal points = order.getPointsUsed() != null ? order.getPointsUsed() : BigDecimal.ZERO;
                    BigDecimal discounts = order.getDiscounts() != null ? order.getDiscounts() : BigDecimal.ZERO;
                    return points.add(discounts);
                })
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        sectionBuilder.append("- **Total Rakuten Orders:** ").append(orders.size()).append("\n");
        sectionBuilder.append("- **Total Billed Amount:** ").append(totalBilled).append(" JPY\n");
        sectionBuilder.append("- **Total Saved (Points/Discounts):** ").append(totalSaved).append(" JPY\n");

        sectionBuilder.append("\n### Order Summary\n");
        sectionBuilder.append("| Date | Order ID | Billed Amount | Saved | Summary |\n|---|---|---|---|---|\n");
        for (RakutenOrder order : orders) {
            String summary;
            if (order.getItems() == null || order.getItems().isEmpty()) {
                summary = "No items";
            } else if (order.getItems().size() == 1) {
                RakutenItem firstItem = order.getItems().get(0);
                summary = PrivacyMaskingUtil.shouldMask(currentUserId, order.getOwnerUserId(),
                        firstItem.getPrivacyLevel(), order.getPrivacyLevel())
                                ? PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.RAKUTEN)
                                : firstItem.getName();
            } else {
                RakutenItem firstItem = order.getItems().get(0);
                String itemName = PrivacyMaskingUtil.shouldMask(currentUserId, order.getOwnerUserId(),
                        firstItem.getPrivacyLevel(), order.getPrivacyLevel())
                                ? PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.RAKUTEN)
                                : firstItem.getName();
                summary = itemName + " and " + (order.getItems().size() - 1)
                        + " other item(s)";
            }

            // Limit summary length for table
            if (summary.length() > 40) {
                summary = summary.substring(0, 37) + "...";
            }

            // Escape pipe characters to avoid breaking the markdown table
            summary = summary.replace("|", "\\|");

            BigDecimal orderSaved = (order.getPointsUsed() != null ? order.getPointsUsed() : BigDecimal.ZERO)
                    .add(order.getDiscounts() != null ? order.getDiscounts() : BigDecimal.ZERO);

            sectionBuilder.append(String.format("| %s | %s | %s JPY | %s JPY | %s |\n",
                    order.getOrderDate(), order.getOrderId(), order.getBilledAmount(), orderSaved,
                    summary));
        }

        sectionBuilder.append("\n---\n\n### Order Details\n\n");
        for (RakutenOrder order : orders) {
            sectionBuilder.append(String.format("**[%s] Order ID: %s**\n", order.getOrderDate(), order.getOrderId()));

            if (order.getItems() != null) {
                for (RakutenItem item : order.getItems()) {
                    String itemName = PrivacyMaskingUtil.shouldMask(currentUserId, order.getOwnerUserId(),
                            item.getPrivacyLevel(), order.getPrivacyLevel())
                                    ? PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.RAKUTEN)
                                    : item.getName();
                    if (item.getSeller() != null && !item.getSeller().isEmpty()
                            && item.getItemId() != null && !item.getItemId().isEmpty()) {
                        sectionBuilder.append(String.format("- [%s](https://item.rakuten.co.jp/%s/%s/) (%s JPY x %d)\n",
                                itemName, item.getSeller(), item.getItemId(), item.getUnitPrice(), item.getQuantity()));
                    } else {
                        sectionBuilder.append(String.format("- %s (%s JPY x %d)\n",
                                itemName, item.getUnitPrice(), item.getQuantity()));
                    }
                }
            }

            if (order.getPayments() != null && !order.getPayments().isEmpty()) {
                String paymentMethods = order.getPayments().stream()
                        .map(payment -> payment.getMethod())
                        .collect(Collectors.joining(", "));
                sectionBuilder.append(String.format("- **Payment Method:** %s\n", paymentMethods));
            }
            sectionBuilder.append("\n");
        }

        return totalBilled;
    }
}
