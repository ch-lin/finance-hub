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
package ch.lin.finance.hub.backend.api.app.service.accounting.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.app.repository.accounting.category.TransactionCategoryRepository;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link AiCategorizationService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCategorizationServiceImpl implements AiCategorizationService {

    /**
     * Prompt for LLMs (e.g., ChatGPT, Claude) to categorize the exported CSV.
     * Includes rules for isEssential and isSimulationDuplicate.
     */
    public static final String CATEGORIZATION_PROMPT = """
            You are a personal finance assistant. Please categorize the following CSV transactions.
            For each row, provide: categoryName, isEssential, isSimulationDuplicate.

            Rules:
            1. categoryName: The name of the category (e.g. "Groceries", "Utilities", "Housing", "Investments").
            2. isEssential: true if the expense is necessary for living (e.g. rent, basic food, utilities, mortgage). false if it's a luxury or non-essential.
            3. isSimulationDuplicate: true if this transaction is a duplicate in the context of simulation (e.g. paying off a credit card bill, transferring money to investments like NISA/iDeCo, or paying a mortgage).
               - If it's just a normal purchase, this should be false.
               - Credit card payments, inter-account transfers, investment deposits (NISA, iDeCo, etc.), and Mortgage/Housing loan payments MUST be true because the simulation engine calculates them automatically based on linked records.

            Return ONLY the updated CSV with the new columns filled.
            """;

    /** The entity manager used for database operations. */
    private final EntityManager entityManager;

    /** The repository for transaction categories. */
    private final TransactionCategoryRepository categoryRepository;

    /**
     * Exports uncategorized items into a CSV format.
     *
     * @return The CSV string of uncategorized items.
     */
    @Override
    @Transactional(readOnly = true)
    public String exportUncategorizedItems(LocalDate startDate, LocalDate endDate, List<String> sources) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,source,orderDate,name,unitPrice,categoryName,isEssential,isSimulationDuplicate\n");

        if (sources == null || sources.isEmpty()) {
            sources = List.of("AMAZON", "RAKUTEN", "SOFMAP", "BANK", "CREDIT_CARD");
        }

        if (sources.contains("AMAZON")) {
            appendAmazonItems(csv, startDate, endDate);
        }
        if (sources.contains("RAKUTEN")) {
            appendRakutenItems(csv, startDate, endDate);
        }
        if (sources.contains("SOFMAP")) {
            appendSofmapItems(csv, startDate, endDate);
        }
        if (sources.contains("BANK")) {
            appendBankTransactions(csv, startDate, endDate);
        }
        if (sources.contains("CREDIT_CARD")) {
            appendCreditCardTransactions(csv, startDate, endDate);
        }

        return csv.toString();
    }

    private void appendAmazonItems(StringBuilder csv, LocalDate startDate, LocalDate endDate) {
        String baseQuery = "SELECT i FROM AmazonItem i WHERE i.category IS NULL";
        if (startDate != null)
            baseQuery += " AND i.order.orderDate >= :startDate";
        if (endDate != null)
            baseQuery += " AND i.order.orderDate <= :endDate";
        TypedQuery<AmazonItem> q = entityManager.createQuery(baseQuery, AmazonItem.class);
        if (startDate != null)
            q.setParameter("startDate", startDate);
        if (endDate != null)
            q.setParameter("endDate", endDate);

        for (AmazonItem item : q.getResultList()) {
            String date = item.getOrder() != null && item.getOrder().getOrderDate() != null
                    ? item.getOrder().getOrderDate().toString()
                    : "";
            appendCsvLine(csv, item.getId(), "AMAZON", date, item.getName(), item.getUnitPrice().toString(), "", "",
                    "");
        }
    }

    private void appendRakutenItems(StringBuilder csv, LocalDate startDate, LocalDate endDate) {
        String baseQuery = "SELECT i FROM RakutenItem i WHERE i.category IS NULL";
        if (startDate != null)
            baseQuery += " AND i.order.orderDate >= :startDate";
        if (endDate != null)
            baseQuery += " AND i.order.orderDate <= :endDate";
        TypedQuery<RakutenItem> q = entityManager.createQuery(baseQuery, RakutenItem.class);
        if (startDate != null)
            q.setParameter("startDate", startDate);
        if (endDate != null)
            q.setParameter("endDate", endDate);

        for (RakutenItem item : q.getResultList()) {
            String date = item.getOrder() != null && item.getOrder().getOrderDate() != null
                    ? item.getOrder().getOrderDate().toString()
                    : "";
            appendCsvLine(csv, item.getId(), "RAKUTEN", date, item.getName(), item.getUnitPrice().toString(), "", "",
                    "");
        }
    }

    private void appendSofmapItems(StringBuilder csv, LocalDate startDate, LocalDate endDate) {
        String baseQuery = "SELECT i FROM SofmapItem i WHERE i.category IS NULL";
        if (startDate != null)
            baseQuery += " AND i.order.orderDate >= :startDate";
        if (endDate != null)
            baseQuery += " AND i.order.orderDate <= :endDate";
        TypedQuery<SofmapItem> q = entityManager.createQuery(baseQuery, SofmapItem.class);
        if (startDate != null)
            q.setParameter("startDate", startDate);
        if (endDate != null)
            q.setParameter("endDate", endDate);

        for (SofmapItem item : q.getResultList()) {
            String date = item.getOrder() != null && item.getOrder().getOrderDate() != null
                    ? item.getOrder().getOrderDate().toString()
                    : "";
            appendCsvLine(csv, item.getId(), "SOFMAP", date, item.getName(), item.getUnitPrice().toString(), "", "",
                    "");
        }
    }

    private void appendBankTransactions(StringBuilder csv, LocalDate startDate, LocalDate endDate) {
        String baseQuery = "SELECT t FROM BankTransaction t WHERE t.category IS NULL";
        if (startDate != null)
            baseQuery += " AND t.transactionDate >= :startDate";
        if (endDate != null)
            baseQuery += " AND t.transactionDate <= :endDate";
        TypedQuery<BankTransaction> q = entityManager.createQuery(baseQuery, BankTransaction.class);
        if (startDate != null)
            q.setParameter("startDate", startDate);
        if (endDate != null)
            q.setParameter("endDate", endDate);

        for (BankTransaction tx : q.getResultList()) {
            String date = tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : "";
            Integer amount = tx.getWithdrawalAmount() != null ? tx.getWithdrawalAmount() : tx.getDepositAmount();
            String amountStr = amount != null ? amount.toString() : "0";
            appendCsvLine(csv, tx.getId(), "BANK", date, tx.getDescription(), amountStr, "", "", "");
        }
    }

    private void appendCreditCardTransactions(StringBuilder csv, LocalDate startDate, LocalDate endDate) {
        String baseQuery = "SELECT t FROM CreditCardTransaction t WHERE t.category IS NULL";
        if (startDate != null)
            baseQuery += " AND t.transactionDate >= :startDate";
        if (endDate != null)
            baseQuery += " AND t.transactionDate <= :endDate";
        TypedQuery<CreditCardTransaction> q = entityManager.createQuery(baseQuery, CreditCardTransaction.class);
        if (startDate != null)
            q.setParameter("startDate", startDate);
        if (endDate != null)
            q.setParameter("endDate", endDate);

        for (CreditCardTransaction tx : q.getResultList()) {
            String date = tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : "";
            String amountStr = tx.getAmount() != null ? tx.getAmount().toString() : "0";
            appendCsvLine(csv, tx.getId(), "CREDIT_CARD", date, tx.getStoreName(), amountStr, "", "", "");
        }
    }

    private void appendCsvLine(StringBuilder csv, Long id, String source, String date, String name, String price,
            String categoryName, String isEssential, String isSimulationDuplicate) {
        csv.append(id).append(",")
                .append(escapeCsv(source)).append(",")
                .append(escapeCsv(date)).append(",")
                .append(escapeCsv(name)).append(",")
                .append(price).append(",")
                .append(escapeCsv(categoryName)).append(",")
                .append(escapeCsv(isEssential)).append(",")
                .append(escapeCsv(isSimulationDuplicate)).append("\n");
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Imports categorized items from a CSV file and updates them in the database.
     *
     * @param file The CSV file.
     * @throws IOException If an error occurs reading the file.
     */
    @Override
    @Transactional
    public void importCategorizedItems(MultipartFile file) throws IOException {
        String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        String[] lines = csvContent.split("\\r?\\n");
        // skip header
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = parseCsvLine(line);
            if (parts.length < 8) {
                continue;
            }
            try {
                Long id = Long.valueOf(parts[0].trim());
                String source = parts[1].trim();
                String categoryName = parts[5].trim();
                boolean isEssential = Boolean.parseBoolean(parts[6].trim());
                boolean isSimulationDuplicate = Boolean.parseBoolean(parts[7].trim());

                if (categoryName.isEmpty()) {
                    continue;
                }

                TransactionCategory category = categoryRepository.findByName(categoryName)
                        .orElseGet(() -> {
                            TransactionCategory newCategory = new TransactionCategory(categoryName, isEssential,
                                    isSimulationDuplicate, (TransactionCategory) null);
                            return categoryRepository.save(newCategory);
                        });

                switch (source) {
                    case "AMAZON" -> {
                        AmazonItem item = entityManager.find(AmazonItem.class, id);
                        if (item != null)
                            item.setCategory(category);
                    }
                    case "RAKUTEN" -> {
                        RakutenItem item = entityManager.find(RakutenItem.class, id);
                        if (item != null)
                            item.setCategory(category);
                    }
                    case "SOFMAP" -> {
                        SofmapItem item = entityManager.find(SofmapItem.class, id);
                        if (item != null)
                            item.setCategory(category);
                    }
                    case "BANK" -> {
                        BankTransaction item = entityManager.find(BankTransaction.class, id);
                        if (item != null)
                            item.setCategory(category);
                    }
                    case "CREDIT_CARD" -> {
                        CreditCardTransaction item = entityManager.find(CreditCardTransaction.class, id);
                        if (item != null)
                            item.setCategory(category);
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Failed to parse id on line {}: {}", i, line);
            }
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(String[]::new);
    }
}
