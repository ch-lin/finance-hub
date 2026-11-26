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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.command.CreateBankTransactionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.result.BankTransactionResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;

class BankTransactionMapperTest {

    private final BankTransactionMapper mapper = Mappers.getMapper(BankTransactionMapper.class);

    @Test
    void toResponse_ShouldApplyMasking_WhenPrivacyMaskingUtilReturnsTrue() {
        BankTransactionResult result = new BankTransactionResult();
        result.setOwnerUserId("owner-id");
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);
        result.setDescription("Secret Transaction");
        result.setMemo("Top Secret Memo");

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil
                    .when(() -> PrivacyMaskingUtil.shouldMask("current-user", "owner-id", PrivacyLevel.PRIVATE))
                    .thenReturn(true);
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.BANK))
                    .thenReturn("Masked Bank Transaction");

            BankTransactionResponse response = mapper.toResponse(result, "current-user");

            assertEquals("Masked Bank Transaction", response.getDescription());
            assertNull(response.getMemo());
        }
    }

    @Test
    void toResponse_ShouldNotApplyMasking_WhenPrivacyMaskingUtilReturnsFalse() {
        BankTransactionResult result = new BankTransactionResult();
        result.setOwnerUserId("owner-id");
        result.setPrivacyLevel(PrivacyLevel.SHARED);
        result.setDescription("Normal Transaction");
        result.setMemo("Normal Memo");

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any()))
                    .thenReturn(false);

            BankTransactionResponse response = mapper.toResponse(result, "current-user");

            assertEquals("Normal Transaction", response.getDescription());
            assertEquals("Normal Memo", response.getMemo());
        }
    }

    @Test
    void toCommand_MapsCorrectly() {
        BankTransactionRequest request = new BankTransactionRequest();
        request.setBankName("CTBC");
        request.setTransactionDate(LocalDate.of(2025, 4, 1));
        request.setDescription("Test Request");
        request.setDepositAmount(100);
        request.setWithdrawalAmount(0);
        request.setMemo("Request Memo");

        CreateBankTransactionCommand command = mapper.toCommand(request);

        assertEquals("CTBC", command.getBankName());
        assertEquals(LocalDate.of(2025, 4, 1), command.getTransactionDate());
        assertEquals("Test Request", command.getDescription());
        assertEquals(100, command.getDepositAmount());
        assertEquals(0, command.getWithdrawalAmount());
        assertEquals("Request Memo", command.getMemo());
    }

    @Test
    void map_WithNulls_ReturnsNull() {
        assertNull(mapper.toCommand(null));
        assertNull(mapper.toResponse(null, "user-id"));
    }
}
