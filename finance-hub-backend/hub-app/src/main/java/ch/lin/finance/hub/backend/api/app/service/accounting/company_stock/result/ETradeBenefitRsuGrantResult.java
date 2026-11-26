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
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuGrant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeBenefitRsuGrantResult extends BaseResult {
    private Long id; // From ETradeBenefitRsuGrant.id (BaseEntity) -> CompanyStockResponse.RsuGrant.id
    private String grantNumber; // From ETradeBenefitRsuGrant.grantNumber -> CompanyStockResponse.RsuGrant.grantNumber
    private LocalDate grantDate; // From ETradeBenefitRsuGrant.grantDate -> CompanyStockResponse.RsuGrant.grantDate
    private Double grantedQty; // From ETradeBenefitRsuGrant.grantedQty -> CompanyStockResponse.RsuGrant.grantedQty
    private Double vestedQty; // From ETradeBenefitRsuGrant.vestedQty -> CompanyStockResponse.RsuGrant.vestedQty
    private Double sellableQty; // From ETradeBenefitRsuGrant.sellableQty -> CompanyStockResponse.RsuGrant.sellableQty
    private MonetaryValue estMarketValue; // From ETradeBenefitRsuGrant.estMarketValue -> CompanyStockResponse.RsuGrant.estMarketValue

    public static ETradeBenefitRsuGrantResult fromEntity(ETradeBenefitRsuGrant entity) {
        if (entity == null) {
            return null;
        }
        ETradeBenefitRsuGrantResult result = new ETradeBenefitRsuGrantResult();
        result.setId(entity.getId());
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setGrantNumber(entity.getGrantNumber());
        result.setGrantDate(entity.getGrantDate());
        result.setGrantedQty(entity.getGrantedQty());
        result.setVestedQty(entity.getVestedQty());
        result.setSellableQty(entity.getSellableQty());
        result.setEstMarketValue(MonetaryValue.fromEntity(entity.getEstMarketValue()));
        return result;
    }
}

