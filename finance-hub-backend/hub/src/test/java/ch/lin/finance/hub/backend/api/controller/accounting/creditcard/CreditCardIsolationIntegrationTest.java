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
package ch.lin.finance.hub.backend.api.controller.accounting.creditcard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardTransactionRepository;
import ch.lin.finance.hub.backend.api.controller.security.AbstractMultiUserIsolationIntegrationTest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;

class CreditCardIsolationIntegrationTest extends AbstractMultiUserIsolationIntegrationTest {

    @Autowired
    private CreditCardTransactionRepository creditCardTransactionRepository;

    @Override
    protected void cleanUpModuleData() {
        creditCardTransactionRepository.deleteAll();
    }

    @Test
    void creditCard_shouldIsolatePersonalScopeAndIncludeSharedAndAggregateOnlyInFamilyScope() throws Exception {
        LocalDate txDate = LocalDate.of(2026, 2, 15);

        setAuthenticatedUser(USER_A);
        CreditCardTransaction txA = new CreditCardTransaction("Card-A", txDate, "Store-A", 1000);
        txA.setPrivacyLevel(PrivacyLevel.PRIVATE);
        creditCardTransactionRepository.save(txA);

        setAuthenticatedUser(USER_B);
        CreditCardTransaction txBShared = new CreditCardTransaction("Card-B-Shared", txDate, "Store-B-Shared", 2000);
        txBShared.setPrivacyLevel(PrivacyLevel.SHARED);
        creditCardTransactionRepository.save(txBShared);

        CreditCardTransaction txBAgg = new CreditCardTransaction("Card-B-Agg", txDate, "Store-B-Agg", 3000);
        txBAgg.setPrivacyLevel(PrivacyLevel.AGGREGATE_ONLY);
        creditCardTransactionRepository.save(txBAgg);

        CreditCardTransaction txBPrivate = new CreditCardTransaction("Card-B-Private", txDate, "Store-B-Private", 4000);
        txBPrivate.setPrivacyLevel(PrivacyLevel.PRIVATE);
        creditCardTransactionRepository.save(txBPrivate);

        setAuthenticatedUser(USER_C);
        CreditCardTransaction txCShared = new CreditCardTransaction("Card-C-Shared", txDate, "Store-C-Shared", 9000);
        txCShared.setPrivacyLevel(PrivacyLevel.SHARED);
        creditCardTransactionRepository.save(txCShared);

        SecurityContextHolder.clearContext();

        // Personal scope: User A sees ONLY Card-A
        mockMvc.perform(get("/credit-cards")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("Card-A"));

        mockMvc.perform(get("/credit-cards/transactions")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].cardName").value("Card-A"));

        mockMvc.perform(get("/credit-cards/total-amounts")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Card-A']").value(1000))
                .andExpect(jsonPath("$['Card-B-Shared']").doesNotExist())
                .andExpect(jsonPath("$['Card-B-Agg']").doesNotExist())
                .andExpect(jsonPath("$['Card-B-Private']").doesNotExist())
                .andExpect(jsonPath("$['Card-C-Shared']").doesNotExist());

        // Family scope: includes User A + User B SHARED & AGGREGATE_ONLY; excludes
        // User B PRIVATE & non-family User C
        MvcResult familyCardsRes = mockMvc.perform(get("/credit-cards")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> familyCards = new ArrayList<>();
        objectMapper.readTree(familyCardsRes.getResponse().getContentAsString())
                .forEach(node -> familyCards.add(node.asText()));
        assertThat(familyCards).containsExactlyInAnyOrder("Card-A", "Card-B-Shared", "Card-B-Agg");

        mockMvc.perform(get("/credit-cards/transactions")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));

        mockMvc.perform(get("/credit-cards/total-amounts")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Card-A']").value(1000))
                .andExpect(jsonPath("$['Card-B-Shared']").value(2000))
                .andExpect(jsonPath("$['Card-B-Agg']").value(3000))
                .andExpect(jsonPath("$['Card-B-Private']").doesNotExist())
                .andExpect(jsonPath("$['Card-C-Shared']").doesNotExist());
    }
}
