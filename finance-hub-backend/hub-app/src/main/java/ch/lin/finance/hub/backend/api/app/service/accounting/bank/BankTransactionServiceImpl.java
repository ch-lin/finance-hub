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

import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankStatementRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.bank.BankTransactionRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.CreateBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link BankTransactionService} that interacts with the
 * database to store and retrieve bank statements and transactions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankTransactionServiceImpl implements BankTransactionService {

    /**
     * Repository for accessing bank statement entities.
     */
    private final BankStatementRepository statementRepository;

    /**
     * Repository for accessing bank transaction entities.
     */
    private final BankTransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<BankStatementResult> getStatement(String bankName, String statementMonth) {
        return statementRepository.findByBankNameAndStatementMonth(bankName, statementMonth)
                .map(BankStatementResult::fromEntity);
    }

    @Override
    @Transactional
    public BankStatementResult saveStatement(SaveBankStatementCommand command) {
        BankStatement newStatement = new BankStatement(command.getBankName(), command.getStatementMonth());
        if (command.getTransactions() != null) {
            for (SaveBankTransactionCommand txCmd : command.getTransactions()) {
                java.time.LocalDate txDate = txCmd.getTransactionDate() != null
                        ? java.time.LocalDate.parse(txCmd.getTransactionDate())
                        : null;
                BankTransaction tx = new BankTransaction(command.getBankName(), txDate, txCmd.getDescription(),
                        txCmd.getDepositAmount(), txCmd.getWithdrawalAmount(), txCmd.getBalance(), txCmd.getMemo());
                newStatement.addTransaction(tx);
            }
        }

        BankStatement statement = statementRepository
                .findByBankNameAndStatementMonth(newStatement.getBankName(), newStatement.getStatementMonth())
                .map(existingStatement -> {
                    log.info("Updating existing BankStatement for {} - {}", existingStatement.getBankName(),
                            existingStatement.getStatementMonth());
                    // Merge new transactions with existing ones.
                    // Updates fact fields (memo) and preserves user-defined fields (category).
                    int newCount = existingStatement.mergeTransactions(newStatement.getTransactions());
                    log.info("Merged {} new transactions into existing statement", newCount);
                    return statementRepository.save(existingStatement);
                })
                .orElseGet(() -> {
                    log.info("Creating new BankStatement for {} - {}", newStatement.getBankName(),
                            newStatement.getStatementMonth());
                    return statementRepository.save(newStatement);
                });
        return BankStatementResult.fromEntity(statement);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BankTransactionResult> getTransactions(List<String> bankNames, LocalDate startDate, LocalDate endDate,
            Boolean isIncome, Pageable pageable) {
        Specification<BankTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (bankNames != null && !bankNames.isEmpty()) {
                predicates.add(root.get("bankName").in(bankNames));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }

            if (isIncome != null) {
                if (isIncome) {
                    predicates.add(cb.isNotNull(root.get("depositAmount")));
                    predicates.add(cb.greaterThan(root.get("depositAmount"), 0));
                } else {
                    predicates.add(cb.isNotNull(root.get("withdrawalAmount")));
                    predicates.add(cb.greaterThan(root.get("withdrawalAmount"), 0));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return transactionRepository.findAll(spec, Objects.requireNonNull(pageable))
                .map(BankTransactionResult::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getLatestBankBalances() {
        List<String> bankNames = transactionRepository.findDistinctBankNames();
        Map<String, Integer> balances = new HashMap<>();
        for (String bankName : bankNames) {
            BankTransaction latestTx = transactionRepository.findFirstByBankNameOrderByTransactionDateDesc(bankName);
            if (latestTx != null && latestTx.getBalance() != null) {
                balances.put(bankName, latestTx.getBalance());
            }
        }
        return balances;
    }

    @Override
    @Transactional
    public BankTransactionResult addTransaction(CreateBankTransactionCommand command) {
        String statementMonth = command.getTransactionDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        BankStatement statement = statementRepository
                .findByBankNameAndStatementMonth(command.getBankName(), statementMonth)
                .orElseGet(() -> statementRepository.save(new BankStatement(command.getBankName(), statementMonth)));

        BankTransaction transaction = new BankTransaction(
                command.getBankName(),
                command.getTransactionDate(),
                command.getDescription(),
                command.getDepositAmount(),
                command.getWithdrawalAmount(),
                null, // Balance can be null for manual entries
                command.getMemo()
        );

        statement.addTransaction(transaction);

        return BankTransactionResult.fromEntity(transactionRepository.save(transaction));
    }
}
