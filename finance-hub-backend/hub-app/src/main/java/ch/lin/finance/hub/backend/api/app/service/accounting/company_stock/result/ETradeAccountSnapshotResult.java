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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.app.service.base.MonetaryValue;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeAccountSnapshot;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeAccountSnapshotResult extends BaseResult {
    private Long id; // From ETradeAccountSnapshot.id (BaseEntity) -> CompanyStockResponse.Snapshot.id
    private LocalDate recordDate; // From ETradeAccountSnapshot.recordDate -> CompanyStockResponse.Snapshot.recordDate
    private MonetaryValue totalAccountValue; // From ETradeAccountSnapshot.totalAccountValue -> CompanyStockResponse.Snapshot.totalAccountValue
    private MonetaryValue currentAccountValue; // From ETradeAccountSnapshot.currentAccountValue -> CompanyStockResponse.Snapshot.currentAccountValue
    private MonetaryValue potentialBenefitValue; // From ETradeAccountSnapshot.potentialBenefitValue -> CompanyStockResponse.Snapshot.potentialBenefitValue
    private Double esppPurchasedQty; // From ETradeAccountSnapshot.esppPurchasedQty -> CompanyStockResponse.Snapshot.esppPurchasedQty
    private Double rsGrantedQty; // From ETradeAccountSnapshot.rsGrantedQty -> CompanyStockResponse.Snapshot.rsGrantedQty
    private Double exchangeRate; // From ETradeAccountSnapshot.exchangeRate -> CompanyStockResponse.Snapshot.exchangeRate

    public static ETradeAccountSnapshotResult fromEntity(ETradeAccountSnapshot entity) {
        if (entity == null) {
            return null;
        }
        ETradeAccountSnapshotResult result = new ETradeAccountSnapshotResult();
        result.setId(entity.getId());
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setRecordDate(entity.getRecordDate());
        result.setTotalAccountValue(MonetaryValue.fromEntity(entity.getTotalAccountValue()));
        result.setCurrentAccountValue(MonetaryValue.fromEntity(entity.getCurrentAccountValue()));
        result.setPotentialBenefitValue(MonetaryValue.fromEntity(entity.getPotentialBenefitValue()));
        result.setEsppPurchasedQty(entity.getEsppPurchasedQty());
        result.setRsGrantedQty(entity.getRsGrantedQty());
        result.setExchangeRate(entity.getExchangeRate());
        return result;
    }
}

