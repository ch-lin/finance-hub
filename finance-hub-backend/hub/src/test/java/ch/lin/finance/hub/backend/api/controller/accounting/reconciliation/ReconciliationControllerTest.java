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
package ch.lin.finance.hub.backend.api.controller.accounting.reconciliation;

import java.util.Collections;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.result.CreditCardTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.ReconciliationService;
import ch.lin.finance.hub.backend.api.app.service.accounting.reconciliation.command.LinkTransactionCommand;
import ch.lin.finance.hub.backend.api.controller.accounting.reconciliation.dto.LinkTransactionRequest;

@ExtendWith(MockitoExtension.class)
class ReconciliationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private ch.lin.finance.hub.backend.api.controller.accounting.reconciliation.dto.ReconciliationMapper reconciliationMapper;

    @InjectMocks
    private ReconciliationController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("Should return unmatched transactions")
    void getUnmatchedTransactions_ReturnsPage() throws Exception {
        Page<CreditCardTransactionResult> page = new PageImpl<>(
                Objects.requireNonNull(Collections.<CreditCardTransactionResult>emptyList()),
                org.springframework.data.domain.PageRequest.of(0, 20), 0);

        when(reconciliationService.getUnmatchedAmazonTransactions(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/reconciliation/unmatched")
                .param("page", "0")
                .param("size", "20"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk());

        verify(reconciliationService, times(1)).getUnmatchedAmazonTransactions(any(Pageable.class));
    }

    @Test
    @DisplayName("Should return suggested matches")
    void getSuggestions_ReturnsList() throws Exception {
        when(reconciliationService.getSuggestions(123L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/reconciliation/123/suggestions"))
                .andExpect(status().isOk());

        verify(reconciliationService, times(1)).getSuggestions(123L);
    }

    @Test
    @DisplayName("Should link transaction successfully")
    void linkTransaction_ReturnsOk() throws Exception {
        LinkTransactionRequest request = new LinkTransactionRequest(10L, 20L);
        LinkTransactionCommand command = LinkTransactionCommand.builder().creditCardTransactionId(10L)
                .amazonOrderId(20L).build();
        when(reconciliationMapper.toCommand(any())).thenReturn(command);

        mockMvc.perform(post("/reconciliation/link")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(reconciliationService, times(1)).linkTransaction(10L, 20L);
    }
}
