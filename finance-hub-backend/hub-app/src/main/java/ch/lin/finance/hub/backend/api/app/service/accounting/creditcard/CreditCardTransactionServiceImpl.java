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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardStatementRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.command.CreateCreditCardTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link CreditCardTransactionService} using JPA Criteria API
 * to query and aggregate credit card transactions.
 */
@Service
@RequiredArgsConstructor
public class CreditCardTransactionServiceImpl implements CreditCardTransactionService {

    /**
     * Repository for accessing credit card statement entities.
     */
    private final CreditCardStatementRepository statementRepository;

    /**
     * Repository for accessing credit card transaction entities.
     */
    private final CreditCardTransactionRepository transactionRepository;

    /**
     * Entity manager used for dynamic Criteria API queries.
     */
    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Page<CreditCardTransactionResult> getTransactions(List<String> cardNames, LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        Specification<CreditCardTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (cardNames != null && !cardNames.isEmpty()) {
                predicates.add(root.get("cardName").in(cardNames));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return transactionRepository.findAll(spec, Objects.requireNonNull(pageable))
                .map(CreditCardTransactionResult::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableCardNames() {
        return transactionRepository.findDistinctCardNames();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getTotalAmounts(List<String> cardNames, LocalDate startDate, LocalDate endDate) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CreditCardTransaction> root = query.from(CreditCardTransaction.class);

        List<Predicate> predicates = new ArrayList<>();
        if (cardNames != null && !cardNames.isEmpty()) {
            predicates.add(root.get("cardName").in(cardNames));
        }
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
        }

        query.multiselect(root.get("cardName"), cb.sumAsLong(root.get("amount")));
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(Predicate[]::new)));
        }
        query.groupBy(root.get("cardName"));

        List<Tuple> results = em.createQuery(query).getResultList();
        Map<String, Integer> totals = new HashMap<>();
        for (Tuple tuple : results) {
            String cardName = tuple.get(0, String.class);
            Long sum = tuple.get(1, Long.class);
            if (sum != null) {
                totals.put(cardName, sum.intValue());
            }
        }
        return totals;
    }

    @Override
    @Transactional
    public CreditCardTransactionResult addTransaction(CreateCreditCardTransactionCommand command) {
        String billingMonth = command.getTransactionDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        CreditCardStatement statement = statementRepository
                .findByCardNameAndBillingMonth(command.getCardName(), billingMonth)
                .orElseGet(() -> statementRepository
                        .save(new CreditCardStatement(command.getCardName(), billingMonth, null, 0)));

        CreditCardTransaction transaction = new CreditCardTransaction(
                command.getCardName(),
                command.getTransactionDate(),
                command.getStoreName(),
                command.getAmount());
        transaction.setMemo(command.getMemo());

        statement.addTransaction(transaction);

        return CreditCardTransactionResult.fromEntity(transactionRepository.save(transaction));
    }
}
