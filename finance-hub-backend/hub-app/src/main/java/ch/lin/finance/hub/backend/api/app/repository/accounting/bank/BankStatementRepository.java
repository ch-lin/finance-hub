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
package ch.lin.finance.hub.backend.api.app.repository.accounting.bank;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankStatement;

/**
 * Spring Data JPA repository for {@link BankStatement} entities.
 */
@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, String> {

    /**
     * Finds a bank statement by its bank name and statement month.
     * 
     * @param bankName       The name of the bank (e.g., SMBC, MUFG).
     * @param statementMonth The month of the statement (e.g., 2025-01).
     * @return an Optional containing the BankStatement if found.
     */
    Optional<BankStatement> findByBankNameAndStatementMonth(String bankName, String statementMonth);

}
