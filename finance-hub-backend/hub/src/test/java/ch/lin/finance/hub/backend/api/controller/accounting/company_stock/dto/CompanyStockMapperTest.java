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
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command.SaveETradeExtensionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitEsppPurchaseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeCashTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeEsppStockLotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeRsuStockLotResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.request.ETradeExtensionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.response.CompanyStockResponse;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;

class CompanyStockMapperTest {

    private final CompanyStockMapper mapper = Mappers.getMapper(CompanyStockMapper.class);
    private final String currentUserId = "user-123";
    private final String ownerUserId = "owner-456";

    @Test
    void toCommand_MapsCorrectly() {
        ETradeExtensionRequest request = new ETradeExtensionRequest(
                LocalDate.of(2023, 10, 1),
                null, null, null, null,
                100.0, 50.0, 1.0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        SaveETradeExtensionCommand command = mapper.toCommand(request);
        assertEquals(LocalDate.of(2023, 10, 1), command.getRecordDate());
        assertEquals(100.0, command.getEsppPurchasedQty());
    }

    @Test
    void toSnapshotResponse_WithoutMasking() {
        ETradeAccountSnapshotResult result = new ETradeAccountSnapshotResult();
        result.setEsppPurchasedQty(50.0);
        result.setRsGrantedQty(100.0);
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.SHARED);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(false);

            CompanyStockResponse.Snapshot response = mapper.toSnapshotResponse(result, currentUserId);
            assertEquals(50.0, response.getEsppPurchasedQty());
            assertEquals(100.0, response.getRsGrantedQty());
        }
    }

    @Test
    void toSnapshotResponse_WithMasking() {
        ETradeAccountSnapshotResult result = new ETradeAccountSnapshotResult();
        result.setEsppPurchasedQty(50.0);
        result.setRsGrantedQty(100.0);
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(true);

            CompanyStockResponse.Snapshot response = mapper.toSnapshotResponse(result, currentUserId);
            assertNull(response.getEsppPurchasedQty());
            assertNull(response.getRsGrantedQty());
        }
    }

    @Test
    void toStockLotResponse_Espp_WithMasking() {
        ETradeEsppStockLotResult result = new ETradeEsppStockLotResult();
        result.setPurchasedQty(10.0);
        result.setSellableQuantity(10.0);
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(true);

            CompanyStockResponse.StockLot response = mapper.toStockLotResponse(result, currentUserId);
            assertNull(response.getQuantity());
            assertNull(response.getSellableQuantity());
            if (response instanceof CompanyStockResponse.EsppStockLot espp) {
                assertNull(espp.getPurchasedQty());
            }
        }
    }

    @Test
    void toStockLotResponse_Rsu_WithMasking() {
        ETradeRsuStockLotResult result = new ETradeRsuStockLotResult();
        result.setSellableQuantity(28.0);
        result.setGrantNumber("00136443");
        result.setVestPeriod(1);
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(true);

            CompanyStockResponse.StockLot response = mapper.toStockLotResponse(result, currentUserId);
            assertNull(response.getQuantity());
            assertNull(response.getSellableQuantity());
        }
    }

    @Test
    void toStockLotResponse_Espp_WithoutMasking() {
        ETradeEsppStockLotResult result = new ETradeEsppStockLotResult();
        result.setRecordType(ETradeRecordType.ESPP);
        result.setSymbol("GOOG");
        result.setPurchaseDate(LocalDate.of(2023, 6, 15));
        result.setPurchasedQty(10.0);
        result.setSellableQuantity(10.0);
        result.setDiscountPercent(15.0);
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.SHARED);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(false);

            CompanyStockResponse.StockLot response = mapper.toStockLotResponse(result, currentUserId);
            assertEquals("ESPP", response.getRecordType());
            assertEquals("GOOG", response.getSymbol());
            assertEquals(10.0, response.getQuantity());
            assertEquals(10.0, response.getSellableQuantity());
            assertEquals(CompanyStockResponse.EsppStockLot.class, response.getClass());
            CompanyStockResponse.EsppStockLot espp = (CompanyStockResponse.EsppStockLot) response;
            assertEquals(LocalDate.of(2023, 6, 15), espp.getPurchaseDate());
            assertEquals(10.0, espp.getPurchasedQty());
            assertEquals(15.0, espp.getDiscountPercent());
        }
    }

    @Test
    void toStockLotResponse_Rsu_WithoutMasking() {
        ETradeRsuStockLotResult result = new ETradeRsuStockLotResult();
        result.setRecordType(ETradeRecordType.RSU);
        result.setSymbol("GOOG");
        result.setGrantNumber("00136443");
        result.setVestPeriod(1);
        result.setVestDate(LocalDate.of(2022, 11, 22));
        result.setSellableQuantity(28.0);
        result.setCapitalGainsStatus("Long Term");
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.SHARED);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(false);

            CompanyStockResponse.StockLot response = mapper.toStockLotResponse(result, currentUserId);
            assertEquals("RSU", response.getRecordType());
            assertEquals("GOOG", response.getSymbol());
            assertEquals(28.0, response.getSellableQuantity());
            assertEquals("Long Term", response.getCapitalGainsStatus());
            assertEquals(CompanyStockResponse.RsuStockLot.class, response.getClass());
            CompanyStockResponse.RsuStockLot rsu = (CompanyStockResponse.RsuStockLot) response;
            assertEquals("00136443", rsu.getGrantNumber());
            assertEquals(1, rsu.getVestPeriod());
            assertEquals(LocalDate.of(2022, 11, 22), rsu.getVestDate());
        }
    }

    @Test
    void toCashTransactionResponse_WithMasking() {
        ETradeCashTransactionResult result = new ETradeCashTransactionResult();
        result.setDescription("Dividend");
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(true);
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.COMPANY_STOCK))
                    .thenReturn("Masked Company Stock");

            CompanyStockResponse.CashTransaction response = mapper.toCashTransactionResponse(result, currentUserId);
            assertEquals("Masked Company Stock", response.getDescription());
        }
    }

    @Test
    void toEsppPurchaseResponse_WithMasking() {
        ETradeBenefitEsppPurchaseResult result = new ETradeBenefitEsppPurchaseResult();
        result.setPurchasedQty(20.0);
        result.setOwnerUserId(ownerUserId);
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any())).thenReturn(true);

            CompanyStockResponse.EsppPurchase response = mapper.toEsppPurchaseResponse(result, currentUserId);
            assertNull(response.getPurchasedQty());
        }
    }
}
