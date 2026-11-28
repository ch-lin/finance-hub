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
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock;

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

import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeEsppStockLotRepository;
import ch.lin.finance.hub.backend.api.controller.security.AbstractMultiUserIsolationIntegrationTest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeEsppStockLot;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.MonetaryAmount;

class CompanyStockIsolationIntegrationTest extends AbstractMultiUserIsolationIntegrationTest {

    @Autowired
    private ETradeAccountSnapshotRepository etradeAccountSnapshotRepository;

    @Autowired
    private ETradeEsppStockLotRepository etradeEsppStockLotRepository;

    @Override
    protected void cleanUpModuleData() {
        etradeAccountSnapshotRepository.deleteAll();
        etradeEsppStockLotRepository.deleteAll();
    }

    @Test
    void companyStockETrade_shouldRespectPersonalAndFamilyReadableScope() throws Exception {
        setAuthenticatedUser(USER_A);
        ETradeAccountSnapshot snapA = new ETradeAccountSnapshot(
                "SYM-A", LocalDate.of(2026, 1, 15), 1000L, 1000L, 0L, CurrencyCode.USD);
        snapA.setPrivacyLevel(PrivacyLevel.PRIVATE);
        etradeAccountSnapshotRepository.save(snapA);

        ETradeEsppStockLot lotA = new ETradeEsppStockLot(
                "SYM-A", LocalDate.of(2026, 1, 15), MonetaryAmount.of(100L, CurrencyCode.USD),
                10.0, 10.0, MonetaryAmount.of(1000L, CurrencyCode.USD),
                MonetaryAmount.of(1200L, CurrencyCode.USD), MonetaryAmount.of(200L, CurrencyCode.USD), 15.0);
        lotA.setPrivacyLevel(PrivacyLevel.PRIVATE);
        etradeEsppStockLotRepository.save(lotA);

        setAuthenticatedUser(USER_B);
        ETradeAccountSnapshot snapBShared = new ETradeAccountSnapshot(
                "SYM-B-SHARED", LocalDate.of(2026, 1, 16), 2000L, 2000L, 0L, CurrencyCode.USD);
        snapBShared.setPrivacyLevel(PrivacyLevel.SHARED);
        etradeAccountSnapshotRepository.save(snapBShared);

        ETradeAccountSnapshot snapBPrivate = new ETradeAccountSnapshot(
                "SYM-B-PRIVATE", LocalDate.of(2026, 1, 17), 3000L, 3000L, 0L, CurrencyCode.USD);
        snapBPrivate.setPrivacyLevel(PrivacyLevel.PRIVATE);
        etradeAccountSnapshotRepository.save(snapBPrivate);

        ETradeEsppStockLot lotBShared = new ETradeEsppStockLot(
                "SYM-B-SHARED", LocalDate.of(2026, 1, 16), MonetaryAmount.of(100L, CurrencyCode.USD),
                20.0, 20.0, MonetaryAmount.of(2000L, CurrencyCode.USD),
                MonetaryAmount.of(2400L, CurrencyCode.USD), MonetaryAmount.of(400L, CurrencyCode.USD), 15.0);
        lotBShared.setPrivacyLevel(PrivacyLevel.SHARED);
        etradeEsppStockLotRepository.save(lotBShared);

        ETradeEsppStockLot lotBPrivate = new ETradeEsppStockLot(
                "SYM-B-PRIVATE", LocalDate.of(2026, 1, 17), MonetaryAmount.of(100L, CurrencyCode.USD),
                30.0, 30.0, MonetaryAmount.of(3000L, CurrencyCode.USD),
                MonetaryAmount.of(3600L, CurrencyCode.USD), MonetaryAmount.of(600L, CurrencyCode.USD), 15.0);
        lotBPrivate.setPrivacyLevel(PrivacyLevel.PRIVATE);
        etradeEsppStockLotRepository.save(lotBPrivate);

        setAuthenticatedUser(USER_C);
        ETradeAccountSnapshot snapC = new ETradeAccountSnapshot(
                "SYM-C", LocalDate.of(2026, 1, 18), 9000L, 9000L, 0L, CurrencyCode.USD);
        snapC.setPrivacyLevel(PrivacyLevel.SHARED);
        etradeAccountSnapshotRepository.save(snapC);

        ETradeEsppStockLot lotC = new ETradeEsppStockLot(
                "SYM-C", LocalDate.of(2026, 1, 18), MonetaryAmount.of(100L, CurrencyCode.USD),
                90.0, 90.0, MonetaryAmount.of(9000L, CurrencyCode.USD),
                MonetaryAmount.of(9900L, CurrencyCode.USD), MonetaryAmount.of(900L, CurrencyCode.USD), 15.0);
        lotC.setPrivacyLevel(PrivacyLevel.SHARED);
        etradeEsppStockLotRepository.save(lotC);

        SecurityContextHolder.clearContext();

        // Personal scope
        mockMvc.perform(get("/assets/company-stock/etrade")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recordDate").value("2026-01-15"));

        mockMvc.perform(get("/assets/company-stock/etrade/lots")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("SYM-A"));

        // Family scope: includes User A + User B SHARED; excludes User B PRIVATE & User
        // C
        MvcResult familySnapshotsRes = mockMvc.perform(get("/assets/company-stock/etrade")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> snapshotDates = new ArrayList<>();
        objectMapper.readTree(familySnapshotsRes.getResponse().getContentAsString())
                .forEach(node -> snapshotDates.add(node.get("recordDate").asText()));
        assertThat(snapshotDates).containsExactlyInAnyOrder("2026-01-15", "2026-01-16");

        MvcResult familyLotsRes = mockMvc.perform(get("/assets/company-stock/etrade/lots")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> lotSymbols = new ArrayList<>();
        objectMapper.readTree(familyLotsRes.getResponse().getContentAsString())
                .forEach(node -> lotSymbols.add(node.get("symbol").asText()));
        assertThat(lotSymbols).containsExactlyInAnyOrder("SYM-A", "SYM-B-SHARED");
    }
}
