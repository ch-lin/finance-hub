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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.controller.security.AbstractMultiUserIsolationIntegrationTest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

class AmazonExtensionIsolationIntegrationTest extends AbstractMultiUserIsolationIntegrationTest {

    @Autowired
    private AmazonOrderRepository amazonOrderRepository;

    @Override
    protected void cleanUpModuleData() {
        amazonOrderRepository.deleteAll();
    }

    @Test
    void amazonExtension_shouldIsolateOrdersByOwnerUserId() throws Exception {
        LocalDate orderDate = LocalDate.of(2026, 2, 1);

        setAuthenticatedUser(USER_A);
        AmazonOrder orderASameId = new AmazonOrder("ORD-SAME-ID", orderDate);
        orderASameId.setTotalAmount(new BigDecimal("1200.00"));
        orderASameId.setBilledAmount(new BigDecimal("1200.00"));
        amazonOrderRepository.save(orderASameId);

        setAuthenticatedUser(USER_B);
        AmazonOrder orderBOnly = new AmazonOrder("ORD-B-ONLY", orderDate);
        orderBOnly.setTotalAmount(new BigDecimal("5000.00"));
        orderBOnly.setBilledAmount(new BigDecimal("5000.00"));
        amazonOrderRepository.save(orderBOnly);

        AmazonOrder orderBSameId = new AmazonOrder("ORD-SAME-ID", orderDate);
        orderBSameId.setTotalAmount(new BigDecimal("8800.00"));
        orderBSameId.setBilledAmount(new BigDecimal("8800.00"));
        amazonOrderRepository.save(orderBSameId);

        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/amazon/extension/{orderId}", "ORD-B-ONLY")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());

        MvcResult resA = mockMvc.perform(get("/amazon/extension/{orderId}", "ORD-SAME-ID")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonA = objectMapper.readTree(resA.getResponse().getContentAsString());
        assertThat(jsonA.get("billedAmount").decimalValue()).isEqualByComparingTo("1200.00");

        MvcResult resB = mockMvc.perform(get("/amazon/extension/{orderId}", "ORD-SAME-ID")
                .header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonB = objectMapper.readTree(resB.getResponse().getContentAsString());
        assertThat(jsonB.get("billedAmount").decimalValue()).isEqualByComparingTo("8800.00");
    }
}
