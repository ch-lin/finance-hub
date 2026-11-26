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

import ch.lin.finance.hub.backend.api.app.service.base.MonetaryValue;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeEsppStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeEsppStockLotResult extends StockLotResult {

    private LocalDate purchaseDate;
    private MonetaryValue purchasePrice;
    private Double purchasedQty;
    private MonetaryValue purchaseDateFmv;
    private MonetaryValue grantDateFmv;
    private Double discountPercent;
    private LocalDate grantDate;
    private String qualifiedPlan;
    private LocalDate transferableDate;

    @Override
    public LocalDate getRecordDate() {
        return purchaseDate;
    }

    @Override
    public Double getQuantity() {
        return purchasedQty != null ? purchasedQty : getSellableQuantity();
    }

    @Override
    public Double getDiscountPercent() {
        return discountPercent;
    }

    public static ETradeEsppStockLotResult fromEntity(ETradeEsppStockLot entity) {
        if (entity == null) {
            return null;
        }
        ETradeEsppStockLotResult result = new ETradeEsppStockLotResult();
        result.populateBaseFields(entity, ETradeRecordType.ESPP);
        result.setPurchaseDate(entity.getPurchaseDate());
        result.setPurchasePrice(MonetaryValue.fromEntity(entity.getPurchasePrice()));
        result.setPurchasedQty(entity.getPurchasedQty());
        result.setPurchaseDateFmv(MonetaryValue.fromEntity(entity.getPurchaseDateFmv()));
        result.setGrantDateFmv(MonetaryValue.fromEntity(entity.getGrantDateFmv()));
        result.setDiscountPercent(entity.getDiscountPercent());
        result.setGrantDate(entity.getGrantDate());
        result.setQualifiedPlan(entity.getQualifiedPlan());
        result.setTransferableDate(entity.getTransferableDate());
        return result;
    }
}
