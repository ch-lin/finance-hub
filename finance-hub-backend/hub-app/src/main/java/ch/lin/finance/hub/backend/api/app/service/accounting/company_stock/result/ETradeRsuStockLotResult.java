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

import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRsuStockLot;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeRsuStockLotResult extends StockLotResult {

    private String grantNumber;
    private LocalDate grantDate;
    private Integer vestPeriod;
    private LocalDate vestDate;
    private LocalDate releaseDate;
    private Double pendingSaleQty;

    @Override
    public LocalDate getRecordDate() {
        return vestDate != null ? vestDate : grantDate;
    }

    @Override
    public Double getQuantity() {
        return getSellableQuantity();
    }

    @Override
    public Double getDiscountPercent() {
        return null;
    }

    public static ETradeRsuStockLotResult fromEntity(ETradeRsuStockLot entity) {
        if (entity == null) {
            return null;
        }
        ETradeRsuStockLotResult result = new ETradeRsuStockLotResult();
        result.populateBaseFields(entity, ETradeRecordType.RSU);
        result.setGrantNumber(entity.getGrantNumber());
        result.setGrantDate(entity.getGrantDate());
        result.setVestPeriod(entity.getVestPeriod());
        result.setVestDate(entity.getVestDate());
        result.setReleaseDate(entity.getReleaseDate());
        result.setPendingSaleQty(entity.getPendingSaleQty());
        return result;
    }
}
