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
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.BaseStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeEsppStockLot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRsuStockLot;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class StockLotResult extends BaseResult {

    private Long id;
    private ETradeRecordType recordType;
    private String symbol;
    private Double sellableQuantity;
    private MonetaryValue estCostBasis;
    private MonetaryValue price;
    private MonetaryValue estMarketValue;
    private MonetaryValue expectedGainLoss;
    private MonetaryValue expectedGainLossPerShare;
    private String capitalGainsStatus;

    public LocalDate getRecordDate() {
        return null;
    }

    public Double getQuantity() {
        return sellableQuantity;
    }

    public Double getDiscountPercent() {
        return null;
    }

    protected void populateBaseFields(BaseStockLot entity, ETradeRecordType recordType) {
        setId(entity.getId());
        setOwnerUserId(entity.getOwnerUserId());
        setPrivacyLevel(entity.getPrivacyLevel());
        setRecordType(recordType);
        setSymbol(entity.getSymbol());
        setSellableQuantity(entity.getSellableQuantity());
        setEstCostBasis(MonetaryValue.fromEntity(entity.getEstCostBasis()));
        setPrice(MonetaryValue.fromEntity(entity.getEstCostBasis()));
        setEstMarketValue(MonetaryValue.fromEntity(entity.getEstMarketValue()));
        setExpectedGainLoss(MonetaryValue.fromEntity(entity.getExpectedGainLoss()));
        setExpectedGainLossPerShare(MonetaryValue.fromEntity(entity.getExpectedGainLossPerShare()));
        setCapitalGainsStatus(entity.getCapitalGainsStatus());
    }

    public static StockLotResult fromEntity(BaseStockLot entity) {
        return switch (entity) {
            case null ->
                null;
            case ETradeEsppStockLot espp ->
                ETradeEsppStockLotResult.fromEntity(espp);
            case ETradeRsuStockLot rsu ->
                ETradeRsuStockLotResult.fromEntity(rsu);
            default ->
                null;
        };
    }
}
