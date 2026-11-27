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
package ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeAccountSnapshot;

public interface ETradeAccountSnapshotRepository extends JpaRepository<ETradeAccountSnapshot, String> {

    Optional<ETradeAccountSnapshot> findBySymbolAndRecordDate(String symbol, LocalDate recordDate);

    Optional<ETradeAccountSnapshot> findFirstByOrderByRecordDateDesc();

    Optional<ETradeAccountSnapshot> findFirstByOwnerUserIdOrderByRecordDateDesc(String ownerUserId);
}
