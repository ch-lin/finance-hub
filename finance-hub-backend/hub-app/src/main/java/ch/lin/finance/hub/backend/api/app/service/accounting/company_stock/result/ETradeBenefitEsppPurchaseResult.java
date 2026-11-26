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
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitEsppPurchase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeBenefitEsppPurchaseResult extends BaseResult {
    private Long id; // From ETradeBenefitEsppPurchase.id (BaseEntity) -> CompanyStockResponse.EsppPurchase.id
    private LocalDate purchaseDate; // From ETradeBenefitEsppPurchase.purchaseDate -> CompanyStockResponse.EsppPurchase.purchaseDate
    private MonetaryValue purchasePrice; // From ETradeBenefitEsppPurchase.purchasePrice -> CompanyStockResponse.EsppPurchase.purchasePrice
    private Double purchasedQty; // From ETradeBenefitEsppPurchase.purchasedQty -> CompanyStockResponse.EsppPurchase.purchasedQty
    private Double sellableQty; // From ETradeBenefitEsppPurchase.sellableQty -> CompanyStockResponse.EsppPurchase.sellableQty
    private MonetaryValue estMarketValue; // From ETradeBenefitEsppPurchase.estMarketValue -> CompanyStockResponse.EsppPurchase.estMarketValue
    private LocalDate grantDate; // From ETradeBenefitEsppPurchase.grantDate -> CompanyStockResponse.EsppPurchase.grantDate
    private Double discountPercent; // From ETradeBenefitEsppPurchase.discountPercent -> CompanyStockResponse.EsppPurchase.discountPercent
    private MonetaryValue grantDateFmv; // From ETradeBenefitEsppPurchase.grantDateFmv -> CompanyStockResponse.EsppPurchase.grantDateFmv
    private MonetaryValue purchaseDateFmv; // From ETradeBenefitEsppPurchase.purchaseDateFmv -> CompanyStockResponse.EsppPurchase.purchaseDateFmv
    private Boolean qualifiedPlan; // From ETradeBenefitEsppPurchase.qualifiedPlan -> CompanyStockResponse.EsppPurchase.qualifiedPlan
    private LocalDate transferableDate; // From ETradeBenefitEsppPurchase.transferableDate -> CompanyStockResponse.EsppPurchase.transferableDate

    public static ETradeBenefitEsppPurchaseResult fromEntity(ETradeBenefitEsppPurchase entity) {
        if (entity == null) {
            return null;
        }
        ETradeBenefitEsppPurchaseResult result = new ETradeBenefitEsppPurchaseResult();
        result.setId(entity.getId());
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setPurchaseDate(entity.getPurchaseDate());
        result.setPurchasePrice(MonetaryValue.fromEntity(entity.getPurchasePrice()));
        result.setPurchasedQty(entity.getPurchasedQty());
        result.setSellableQty(entity.getSellableQty());
        result.setEstMarketValue(MonetaryValue.fromEntity(entity.getEstMarketValue()));
        result.setGrantDate(entity.getGrantDate());
        result.setDiscountPercent(entity.getDiscountPercent());
        result.setGrantDateFmv(MonetaryValue.fromEntity(entity.getGrantDateFmv()));
        result.setPurchaseDateFmv(MonetaryValue.fromEntity(entity.getPurchaseDateFmv()));
        result.setQualifiedPlan(entity.getQualifiedPlan());
        result.setTransferableDate(entity.getTransferableDate());
        return result;
    }
}

