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
package ch.lin.finance.hub.backend.api.controller.accounting.bank;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.BankTransactionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.CreateBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankStatementMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankStatementRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankStatementResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankTransactionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankTransactionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.bank.dto.BankTransactionResponse;

@ExtendWith(MockitoExtension.class)
class BankTransactionControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private MockedStatic<SecurityUtil> mockedSecurityUtil;

    @Mock
    private BankTransactionService bankTransactionService;

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @Mock
    private BankStatementMapper bankStatementMapper;

    @InjectMocks
    private BankTransactionController bankTransactionController;

    private static final String TEST_USER_ID = "test-user-id";

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(bankTransactionController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Objects.requireNonNull(objectMapper)))
                .build();

        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(TEST_USER_ID);
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        if (mockedSecurityUtil != null && !mockedSecurityUtil.isClosed()) {
            mockedSecurityUtil.close();
        }
    }

    @Test
    void getBalances_shouldReturnBalancesMap() throws Exception {
        Map<String, Integer> mockBalances = Map.of("CTBC", 1000, "玉山銀行", 500);
        when(bankTransactionService.getLatestBankBalances()).thenReturn(mockBalances);

        mockMvc.perform(get("/banks/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CTBC").value(1000))
                .andExpect(jsonPath("$.['玉山銀行']").value(500));
    }

    @Test
    void getTransactions_shouldReturnPaginatedTransactions() throws Exception {
        BankTransactionResult mockResult = Mockito.mock(BankTransactionResult.class);
        Page<BankTransactionResult> resultPage = new PageImpl<>(
                Objects.requireNonNull(List.of(mockResult)),
                org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(bankTransactionService.getTransactions(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(resultPage);

        BankTransactionResponse mockResponse = BankTransactionResponse.builder().build();
        when(bankTransactionMapper.toResponse(mockResult, TEST_USER_ID)).thenReturn(mockResponse);

        mockMvc.perform(get("/banks/transactions")
                .param("bankNames", "CTBC")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void addTransaction_shouldReturnCreatedTransaction() throws Exception {
        BankTransactionRequest request = BankTransactionRequest.builder()
                .bankName("CTBC")
                .transactionDate(LocalDate.now())
                .description("Test")
                .build();

        CreateBankTransactionCommand mockCommand = CreateBankTransactionCommand.builder().build();
        when(bankTransactionMapper.toCommand(any(BankTransactionRequest.class))).thenReturn(mockCommand);

        BankTransactionResult mockResult = new BankTransactionResult();
        when(bankTransactionService.addTransaction(mockCommand)).thenReturn(mockResult);

        BankTransactionResponse mockResponse = BankTransactionResponse.builder().build();
        when(bankTransactionMapper.toResponse(mockResult, TEST_USER_ID)).thenReturn(mockResponse);

        mockMvc.perform(post("/banks/transactions")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());
    }

    @Test
    void receiveBankStatement_shouldProcessAndReturnStatement() throws Exception {
        BankStatementRequest request = new BankStatementRequest();
        request.setBankName("CTBC");
        request.setStatementMonth("2025-01");
        // request.getTransactions() is null, covering the null branch

        SaveBankStatementCommand mockCommand = SaveBankStatementCommand.builder().build();
        when(bankStatementMapper.toCommand(any(BankStatementRequest.class))).thenReturn(mockCommand);

        BankStatementResult mockResult = new BankStatementResult();
        when(bankTransactionService.saveStatement(mockCommand)).thenReturn(mockResult);

        BankStatementResponse mockResponse = new BankStatementResponse(1L, "CTBC", "2025-01", Collections.emptyList());
        when(bankStatementMapper.toDto(mockResult)).thenReturn(mockResponse);

        mockMvc.perform(post("/banks/statements")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("CTBC"))
                .andExpect(jsonPath("$.statementMonth").value("2025-01"));
    }

    @Test
    void receiveBankStatement_withTransactions_shouldProcessAndReturnStatement() throws Exception {
        BankStatementRequest request = new BankStatementRequest();
        request.setBankName("CTBC");
        request.setStatementMonth("2025-01");
        request.setTransactions(Collections.emptyList()); // Covers the != null branch

        SaveBankStatementCommand mockCommand = SaveBankStatementCommand.builder().build();
        when(bankStatementMapper.toCommand(any(BankStatementRequest.class))).thenReturn(mockCommand);

        BankStatementResult mockResult = new BankStatementResult();
        when(bankTransactionService.saveStatement(mockCommand)).thenReturn(mockResult);

        BankStatementResponse mockResponse = new BankStatementResponse(1L, "CTBC", "2025-01", Collections.emptyList());
        when(bankStatementMapper.toDto(mockResult)).thenReturn(mockResponse);

        mockMvc.perform(post("/banks/statements")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("CTBC"))
                .andExpect(jsonPath("$.statementMonth").value("2025-01"));
    }

    @Test
    void getBankStatement_whenExists_shouldReturnStatement() throws Exception {
        BankStatementResult mockResult = new BankStatementResult();
        when(bankTransactionService.getStatement("CTBC", "2025-01")).thenReturn(Optional.of(mockResult));

        BankStatementResponse mockResponse = new BankStatementResponse(1L, "CTBC", "2025-01", Collections.emptyList());
        when(bankStatementMapper.toDto(mockResult)).thenReturn(mockResponse);

        mockMvc.perform(get("/banks/statements/CTBC/2025-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("CTBC"))
                .andExpect(jsonPath("$.statementMonth").value("2025-01"));
    }

    @Test
    void getBankStatement_whenNotExists_shouldReturnNotFound() throws Exception {
        when(bankTransactionService.getStatement("CTBC", "2025-01")).thenReturn(Optional.empty());

        mockMvc.perform(get("/banks/statements/CTBC/2025-01"))
                .andExpect(status().isNotFound());
    }
}
