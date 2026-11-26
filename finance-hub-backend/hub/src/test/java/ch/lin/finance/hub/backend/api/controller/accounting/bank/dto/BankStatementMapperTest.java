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
package ch.lin.finance.hub.backend.api.controller.accounting.bank.dto;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankStatementCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.SaveBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankStatementResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;

class BankStatementMapperTest {

    private final BankStatementMapper mapper = Mappers.getMapper(BankStatementMapper.class);

    @Test
    void toDto_BankStatementResult_MapsCorrectly() {
        BankTransactionResult txResult = new BankTransactionResult();
        txResult.setId(1L);
        txResult.setBankName("CTBC");
        txResult.setTransactionDate(LocalDate.of(2025, 1, 1));
        txResult.setDescription("Test Desc");
        txResult.setDepositAmount(100);
        txResult.setWithdrawalAmount(0);
        txResult.setBalance(1000);
        txResult.setMemo("Test Memo");
        txResult.setCategoryId(2L);

        BankStatementResult result = new BankStatementResult();
        result.setId(10L);
        result.setBankName("CTBC");
        result.setStatementMonth("2025-01");
        result.setTransactions(List.of(txResult));

        BankStatementResponse response = mapper.toDto(result);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("CTBC", response.bankName());
        assertEquals("2025-01", response.statementMonth());

        assertNotNull(response.transactions());
        assertEquals(1, response.transactions().size());

        BankStatementTransactionResponse txResponse = response.transactions().get(0);
        assertEquals(1L, txResponse.id());
        assertEquals("CTBC", txResponse.bankName());
        assertEquals(LocalDate.of(2025, 1, 1), txResponse.transactionDate());
        assertEquals("Test Desc", txResponse.description());
        assertEquals(100, txResponse.depositAmount());
        assertEquals(0, txResponse.withdrawalAmount());
        assertEquals(1000, txResponse.balance());
        assertEquals("Test Memo", txResponse.memo());
        assertEquals(2L, txResponse.categoryId());
    }

    @Test
    void toDto_BankTransactionResult_MapsCorrectly() {
        BankTransactionResult result = new BankTransactionResult();
        result.setId(2L);
        result.setBankName("E.SUN");
        result.setTransactionDate(LocalDate.of(2025, 2, 1));
        result.setDescription("Buy Food");
        result.setDepositAmount(0);
        result.setWithdrawalAmount(50);
        result.setBalance(500);
        result.setMemo("Lunch");
        result.setCategoryId(3L);

        BankStatementTransactionResponse response = mapper.toDto(result);

        assertNotNull(response);
        assertEquals(2L, response.id());
        assertEquals("E.SUN", response.bankName());
        assertEquals(LocalDate.of(2025, 2, 1), response.transactionDate());
        assertEquals("Buy Food", response.description());
        assertEquals(0, response.depositAmount());
        assertEquals(50, response.withdrawalAmount());
        assertEquals(500, response.balance());
        assertEquals("Lunch", response.memo());
        assertEquals(3L, response.categoryId());
    }

    @Test
    void toCommand_BankStatementRequest_MapsCorrectly() {
        BankStatementTransactionRequest txRequest = new BankStatementTransactionRequest();
        txRequest.setTransactionDate("2025-03-01");
        txRequest.setDescription("Income");
        txRequest.setDepositAmount(2000);
        txRequest.setWithdrawalAmount(0);
        txRequest.setBalance(3000);
        txRequest.setMemo("Salary");

        BankStatementRequest request = new BankStatementRequest();
        request.setBankName("Richart");
        request.setStatementMonth("2025-03");
        request.setTransactions(List.of(txRequest));

        SaveBankStatementCommand command = mapper.toCommand(request);

        assertNotNull(command);
        assertEquals("Richart", command.getBankName());
        assertEquals("2025-03", command.getStatementMonth());

        assertNotNull(command.getTransactions());
        assertEquals(1, command.getTransactions().size());

        SaveBankTransactionCommand txCommand = command.getTransactions().get(0);
        assertEquals("2025-03-01", txCommand.getTransactionDate());
        assertEquals("Income", txCommand.getDescription());
        assertEquals(2000, txCommand.getDepositAmount());
        assertEquals(0, txCommand.getWithdrawalAmount());
        assertEquals(3000, txCommand.getBalance());
        assertEquals("Salary", txCommand.getMemo());
    }

    @Test
    void toDto_Nulls_ReturnNull() {
        assertNull(mapper.toDto((BankStatementResult) null));
        assertNull(mapper.toDto((BankTransactionResult) null));
        assertNull(mapper.toCommand((BankStatementRequest) null));
        assertNull(mapper.toCommand((BankStatementTransactionRequest) null));
    }

    @Test
    void map_WithNullLists_HandlesNullsSafely() {
        BankStatementResult result = new BankStatementResult();
        result.setTransactions(null);
        BankStatementResponse response = mapper.toDto(result);
        assertNotNull(response);
        assertNull(response.transactions());

        BankStatementRequest request = new BankStatementRequest();
        request.setTransactions(null);
        SaveBankStatementCommand command = mapper.toCommand(request);
        assertNotNull(command);
        assertNull(command.getTransactions());
    }
}
