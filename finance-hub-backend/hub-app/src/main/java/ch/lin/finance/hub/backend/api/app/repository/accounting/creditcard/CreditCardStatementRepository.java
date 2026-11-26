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
package ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;

/**
 * Spring Data JPA repository for {@link CreditCardStatement} entities.
 * <p>
 * Provides database access mechanisms for monthly or periodic credit card
 * statements.
 * Each statement encapsulates metadata about the billing cycle and links to
 * individual
 * credit card transactions.
 */
@Repository
public interface CreditCardStatementRepository extends JpaRepository<CreditCardStatement, String> {
    Optional<CreditCardStatement> findByCardNameAndBillingMonth(String cardName, String billingMonth);
}
