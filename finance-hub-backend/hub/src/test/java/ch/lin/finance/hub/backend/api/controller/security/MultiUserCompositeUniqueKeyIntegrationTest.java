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
package ch.lin.finance.hub.backend.api.controller.security;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardStatementRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.RakutenOrderRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.SofmapOrderRepository;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;

class MultiUserCompositeUniqueKeyIntegrationTest extends AbstractMultiUserIsolationIntegrationTest {

    @Autowired
    private CreditCardStatementRepository creditCardStatementRepository;

    @Autowired
    private AmazonOrderRepository amazonOrderRepository;

    @Autowired
    private RakutenOrderRepository rakutenOrderRepository;

    @Autowired
    private SofmapOrderRepository sofmapOrderRepository;

    @Autowired
    private InsurancePolicyRepository insurancePolicyRepository;

    @Override
    protected void cleanUpModuleData() {
        creditCardStatementRepository.deleteAll();
        amazonOrderRepository.deleteAll();
        rakutenOrderRepository.deleteAll();
        sofmapOrderRepository.deleteAll();
        insurancePolicyRepository.deleteAll();
    }

    @Test
    void compositeUniqueKeys_shouldAllowDifferentUsersToPersistSameBusinessKey() {
        LocalDate date = LocalDate.of(2026, 2, 20);

        setAuthenticatedUser(USER_A);
        amazonOrderRepository.saveAndFlush(new AmazonOrder("DUP-AMZ-1", date));
        rakutenOrderRepository.saveAndFlush(new RakutenOrder("DUP-RAK-1", date));
        sofmapOrderRepository.saveAndFlush(new SofmapOrder("DUP-SOF-1", date));
        insurancePolicyRepository.saveAndFlush(new InsurancePolicy(
                "SameCo", InsurancePolicyType.LIFE, "POL-001", "Policy A", 10_000L, date));
        creditCardStatementRepository.saveAndFlush(new CreditCardStatement("SameCard", "2026-02", null, 1000));

        setAuthenticatedUser(USER_B);
        amazonOrderRepository.saveAndFlush(new AmazonOrder("DUP-AMZ-1", date));
        rakutenOrderRepository.saveAndFlush(new RakutenOrder("DUP-RAK-1", date));
        sofmapOrderRepository.saveAndFlush(new SofmapOrder("DUP-SOF-1", date));
        insurancePolicyRepository.saveAndFlush(new InsurancePolicy(
                "SameCo", InsurancePolicyType.LIFE, "POL-001", "Policy B", 20_000L, date));
        creditCardStatementRepository.saveAndFlush(new CreditCardStatement("SameCard", "2026-02", null, 2000));

        assertThat(amazonOrderRepository.count()).isEqualTo(2);
        assertThat(rakutenOrderRepository.count()).isEqualTo(2);
        assertThat(sofmapOrderRepository.count()).isEqualTo(2);
        assertThat(insurancePolicyRepository.count()).isEqualTo(2);
        assertThat(creditCardStatementRepository.count()).isEqualTo(2);

        assertThat(amazonOrderRepository.findByOwnerUserIdAndOrderId(USER_A, "DUP-AMZ-1")).isPresent();
        assertThat(amazonOrderRepository.findByOwnerUserIdAndOrderId(USER_B, "DUP-AMZ-1")).isPresent();
        assertThat(rakutenOrderRepository.findByOwnerUserIdAndOrderId(USER_A, "DUP-RAK-1")).isPresent();
        assertThat(rakutenOrderRepository.findByOwnerUserIdAndOrderId(USER_B, "DUP-RAK-1")).isPresent();
        assertThat(sofmapOrderRepository.findByOwnerUserIdAndOrderId(USER_A, "DUP-SOF-1")).isPresent();
        assertThat(sofmapOrderRepository.findByOwnerUserIdAndOrderId(USER_B, "DUP-SOF-1")).isPresent();
    }
}
